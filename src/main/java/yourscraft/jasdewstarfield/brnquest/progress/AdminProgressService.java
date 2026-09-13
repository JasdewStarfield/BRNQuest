package yourscraft.jasdewstarfield.brnquest.progress;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.api.OperationResult;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.network.BrnQuestNetwork;
import yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerService;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;

import java.util.*;

/** Server-only administrator facade shared by commands and the editor's intent-only protocol. */
public final class AdminProgressService {
    private static final AdminProgressService INSTANCE = new AdminProgressService();
    private static final long CONFIRMATION_NANOS = 30_000_000_000L;
    private static final int MAX_TICKETS = 256;
    private final Map<MinecraftServer, Sessions> servers = new WeakHashMap<>();

    private AdminProgressService() {}
    public static AdminProgressService get() { return INSTANCE; }

    public record Intent(String targetId, String bookId, String revision, String questId, String taskId,
                         AdminProgressAction action) {}
    public record PlayerEntry(String id, String name) {}
    public record View(String targetName, String title, String status, String owner, long taskProgress,
                       int completedTasks, int totalTasks, int claimedRewards, int totalRewards,
                       int automaticRewards, boolean completesQuest, boolean clearsRewardClaims) {}
    public record Reply(OperationResult result, List<PlayerEntry> players, View view, String token) {
        static Reply error(OperationResult result) { return new Reply(result, List.of(), null, ""); }
    }
    /** Equality is the confirmation concurrency guard; it never trusts a client-supplied snapshot. */
    public record State(QuestStatus status, Map<String, Long> tasks, Set<String> claimed,
                        long completedAt, Map<String, QuestStatus> dependencies,
                        Map<UUID, Set<String>> memberClaims, String claimGeneration) {
        public State(QuestStatus status, Map<String, Long> tasks, Set<String> claimed, long completedAt,
                     Map<String, QuestStatus> dependencies, Map<UUID, Set<String>> memberClaims) {
            this(status, tasks, claimed, completedAt, dependencies, memberClaims, "");
        }
    }
    private record Resolved(ServerPlayer player, QuestDefinition quest, String owner) {}
    private record Pending(String actor, Intent intent, String owner, State state, long expires) {}
    private record Completed(String actor, Intent intent, Reply reply, long expires) {}
    private static final class Sessions {
        final Map<String, Pending> pending = new LinkedHashMap<>();
        final Map<String, Completed> completed = new LinkedHashMap<>();
    }

    public Reply catalog(CommandSourceStack actor, String filter) {
        OperationResult denied = permission(actor);
        if (denied != null) return Reply.error(denied);
        if (filter == null || filter.length() > 64) return invalid();
        String query = filter.strip().toLowerCase(Locale.ROOT);
        List<PlayerEntry> players = actor.getServer().getPlayerList().getPlayers().stream()
                .filter(player -> player.getScoreboardName().toLowerCase(Locale.ROOT).contains(query))
                .sorted(Comparator.comparing(ServerPlayer::getScoreboardName, String.CASE_INSENSITIVE_ORDER))
                .limit(64).map(player -> new PlayerEntry(player.getUUID().toString(), player.getScoreboardName())).toList();
        return new Reply(OperationResult.noChange("OK", "Online players"), players, null, "");
    }

    /** A preview is read-only. Its single-use ticket binds the exact effect, actor, owner and live state. */
    public Reply inspect(CommandSourceStack actor, Intent intent, boolean confirmation) {
        OperationResult denied = validate(actor, intent);
        if (denied != null) return Reply.error(denied);
        Resolved resolved = resolve(actor, intent);
        return ProgressEngine.get().synchronizedOwner(resolved.player(), () -> {
            State state = state(resolved.player(), resolved.quest());
            String token = "";
            if (confirmation) {
                Sessions sessions = sessions(actor.getServer());
                String actorId = actorId(actor);
                sessions.pending.entrySet().removeIf(entry -> entry.getValue().actor().equals(actorId));
                token = UUID.randomUUID().toString();
                sessions.pending.put(token, new Pending(actorId, intent, resolved.owner(), state,
                        System.nanoTime() + CONFIRMATION_NANOS));
                trim(sessions.pending);
            }
            return new Reply(OperationResult.noChange("OK", "Current published progress"), List.of(),
                    view(resolved, intent, state), token);
        });
    }

    public Reply confirm(CommandSourceStack actor, String token) {
        OperationResult denied = permission(actor);
        if (denied != null) {
            // A revoked actor cannot reuse a previously authorized preview after regaining permission.
            Pending rejected = actor.getServer().isSameThread() ? sessions(actor.getServer()).pending.get(token) : null;
            if (rejected != null && rejected.actor().equals(actorId(actor))) {
                sessions(actor.getServer()).pending.remove(token);
                ProgressAuditLog.record(actor, rejected.intent(), "", rejected.owner(), rejected.state(), null, denied);
                syncKnownTarget(actor, rejected.intent());
            } else ProgressAuditLog.record(actor, null, "", "", null, null, denied);
            return Reply.error(denied);
        }
        if (token == null || token.length() > 64) return invalid();
        Sessions sessions = sessions(actor.getServer());
        Completed completed = sessions.completed.get(token);
        if (completed != null && completed.actor().equals(actorId(actor))) {
            // Replaying a confirmation must not reset progress again after another gameplay action.
            syncKnownTarget(actor, completed.intent());
            ProgressAuditLog.record(actor, completed.intent(), "", "", null, null,
                    OperationResult.noChange("REPLAY", "Previously processed confirmation; no action repeated"));
            return completed.reply();
        }
        Pending pending = sessions.pending.get(token);
        if (pending == null || !pending.actor().equals(actorId(actor))) {
            OperationResult result = OperationResult.rejected("CONFIRMATION_EXPIRED", "Request a new confirmation");
            ProgressAuditLog.record(actor, null, "", "", null, null, result);
            return Reply.error(result);
        }
        sessions.pending.remove(token);
        Intent intent = pending.intent();
        denied = validate(actor, intent);
        Reply reply;
        if (denied != null) {
            ProgressAuditLog.record(actor, intent, "", pending.owner(), pending.state(), null, denied);
            reply = Reply.error(denied);
            syncKnownTarget(actor, intent);
        } else {
            Resolved resolved = resolve(actor, intent);
            reply = ProgressEngine.get().synchronizedOwner(resolved.player(), () -> {
                State before = state(resolved.player(), resolved.quest());
                OperationResult result;
                if (!pending.owner().equals(resolved.owner()) || !pending.state().equals(before)) {
                    result = OperationResult.staleRevision("PROGRESS_CHANGED", "Progress changed; review again");
                } else {
                    result = ProgressEngine.get().administer(resolved.player(), resolved.quest(),
                            intent.taskId().isEmpty() ? null : ResourceLocation.parse(intent.taskId()), intent.action());
                }
                State after = state(resolved.player(), resolved.quest());
                if (result.changed() && before.equals(after)) {
                    result = OperationResult.noChange("NO_CHANGE", "Progress already has the requested state");
                }
                ProgressAuditLog.record(actor, intent, resolved.player().getScoreboardName(), resolved.owner(),
                        before, after, result);
                sync(resolved.player());
                return new Reply(result, List.of(), view(resolved, intent, after), "");
            });
        }
        sessions.completed.put(token, new Completed(actorId(actor), intent, reply, System.nanoTime() + CONFIRMATION_NANOS));
        trim(sessions.completed);
        return reply;
    }

    /** Commands are already explicit confirmations, but use the identical validation/transaction path. */
    public OperationResult command(CommandSourceStack actor, ServerPlayer target, String questId,
                                   AdminProgressAction action) {
        var active = QuestBookManager.get().active().orElse(null);
        if (active == null) return OperationResult.notReady("NO_BOOK", "No active quest book");
        Intent intent = new Intent(target.getUUID().toString(), active.book().id().toString(), active.revision(),
                questId, "", action);
        Reply preview = inspect(actor, intent, true);
        if (!preview.result().success()) {
            ProgressAuditLog.record(actor, intent, target.getScoreboardName(), "", null, null, preview.result());
            syncKnownTarget(actor, intent);
            return preview.result();
        }
        return confirm(actor, preview.token()).result();
    }

    private OperationResult validate(CommandSourceStack actor, Intent intent) {
        OperationResult denied = permission(actor);
        if (denied != null) return denied;
        if (intent == null || intent.action() == null || !bounded(intent.targetId(), 64)
                || !bounded(intent.bookId(), 256) || !bounded(intent.questId(), 256)
                || !bounded(intent.taskId(), 256) || !bounded(intent.revision(), 128)) {
            return invalid().result();
        }
        UUID targetId;
        try { targetId = UUID.fromString(intent.targetId()); }
        catch (IllegalArgumentException exception) { return invalid().result(); }
        ServerPlayer target = actor.getServer().getPlayerList().getPlayer(targetId);
        if (target == null) return OperationResult.notReady("PLAYER_OFFLINE", "Target player is offline");
        var snapshot = QuestBookManager.get().active().orElse(null);
        if (snapshot == null) return OperationResult.notReady("NO_BOOK", "No active quest book");
        if (!snapshot.book().id().toString().equals(intent.bookId()) || !snapshot.revision().equals(intent.revision())) {
            return OperationResult.staleRevision("STALE_REVISION", "Published quest book changed; reopen the manager");
        }
        ResourceLocation questId = ResourceLocation.tryParse(intent.questId());
        QuestDefinition quest = questId == null ? null : snapshot.quests().get(questId);
        if (quest == null) return OperationResult.rejected("NOT_PUBLISHED", "Publish this quest first");
        if (intent.action().taskAction()) {
            ResourceLocation taskId = ResourceLocation.tryParse(intent.taskId());
            if (taskId == null || quest.tasks().stream().noneMatch(task -> task.id().equals(taskId))) {
                return OperationResult.rejected("NOT_PUBLISHED", "Objective does not belong to the published quest");
            }
        } else if (!intent.taskId().isEmpty()) return invalid().result();
        if (ProgressOwnerService.resolve(target).filter(owner ->
                owner.lifecycle() == yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerLifecycle.ACTIVE).isEmpty()) {
            return OperationResult.notReady("OWNER_UNAVAILABLE", "No active progress owner");
        }
        return null;
    }

    private static OperationResult permission(CommandSourceStack actor) {
        if (!actor.getServer().isSameThread()) return OperationResult.invalid("WRONG_THREAD", "Server thread required");
        if (!actor.hasPermission(2)) return OperationResult.forbidden("FORBIDDEN", "Permission level 2 required");
        return null;
    }

    private static Resolved resolve(CommandSourceStack actor, Intent intent) {
        ServerPlayer player = actor.getServer().getPlayerList().getPlayer(UUID.fromString(intent.targetId()));
        QuestDefinition quest = QuestBookManager.get().active().orElseThrow().quests()
                .get(ResourceLocation.parse(intent.questId()));
        var owner = ProgressOwnerService.require(player);
        return new Resolved(player, quest, owner.providerId() + "/" + owner.ownerId());
    }

    static State state(ServerPlayer player, QuestDefinition quest) {
        PlayerProgress progress = ProgressEngine.get().progress(player);
        Map<String, Long> tasks = new TreeMap<>();
        quest.tasks().forEach(task -> tasks.put(task.id().toString(), progress.taskProgress(task.id().toString())));
        Set<String> claimed = new TreeSet<>();
        quest.rewards().forEach(reward -> { if (ProgressEngine.get().rewardClaimed(player, reward)) claimed.add(reward.id().toString()); });
        Map<String, QuestStatus> dependencies = new TreeMap<>();
        quest.dependencies().forEach(id -> dependencies.put(id.toString(), progress.status(id.toString())));
        return new State(progress.status(quest.id().toString()), Map.copyOf(tasks), Set.copyOf(claimed),
                progress.completedAt(quest.id().toString()), Map.copyOf(dependencies),
                progress.memberClaimsFor(quest.rewards().stream().map(reward -> reward.id().toString()).toList()),
                progress.claimGeneration(quest.id().toString()));
    }

    private static View view(Resolved resolved, Intent intent, State state) {
        QuestDefinition quest = resolved.quest();
        boolean terminal = state.status() == QuestStatus.COMPLETED || state.status() == QuestStatus.REWARD_CLAIMED;
        boolean completes = !terminal && (intent.action() == AdminProgressAction.FORCE_QUEST
                || intent.action() == AdminProgressAction.FORCE_TASK
                && state.dependencies().values().stream().allMatch(status ->
                status == QuestStatus.COMPLETED || status == QuestStatus.REWARD_CLAIMED)
                && quest.tasks().stream().filter(task -> !task.optional()).allMatch(task ->
                task.id().toString().equals(intent.taskId()) || state.tasks().get(task.id().toString()) >= 1));
        String title = quest.title();
        if (intent.action().taskAction()) title = quest.tasks().stream()
                .filter(task -> task.id().toString().equals(intent.taskId()))
                .map(task -> task.config().getOrDefault("title", "").isBlank()
                        ? task.typeId().toString() : task.config().get("title")).findFirst().orElse(title);
        int autoRewards = QuestBookManager.get().active().map(snapshot -> snapshot.book().settings().suppressAutoClaim()).orElse(false) ? 0 : (int) quest.rewards().stream().filter(reward -> reward.policy().automatic()
                && !state.claimed().contains(reward.id().toString())).count();
        return new View(resolved.player().getScoreboardName(), title, state.status().name(), resolved.owner(),
                state.tasks().getOrDefault(intent.taskId(), 0L),
                (int) state.tasks().values().stream().filter(value -> value >= 1).count(), state.tasks().size(),
                state.claimed().size(), quest.rewards().size(), autoRewards, completes,
                intent.action() == AdminProgressAction.RESET_QUEST);
    }

    private Sessions sessions(MinecraftServer server) {
        Sessions result = servers.computeIfAbsent(server, ignored -> new Sessions());
        long now = System.nanoTime();
        result.pending.values().removeIf(value -> value.expires() < now);
        result.completed.values().removeIf(value -> value.expires() < now);
        return result;
    }

    private static <T> void trim(Map<String, T> values) {
        while (values.size() > MAX_TICKETS) values.remove(values.keySet().iterator().next());
    }
    static String actorId(CommandSourceStack actor) {
        return actor.getEntity() == null ? "console:" + actor.getTextName() : actor.getEntity().getUUID().toString();
    }
    private static boolean bounded(String value, int limit) { return value != null && value.length() <= limit; }
    private static Reply invalid() { return Reply.error(OperationResult.invalid("INVALID_REQUEST", "Malformed admin intent")); }
    private static void syncKnownTarget(CommandSourceStack actor, Intent intent) {
        try {
            ServerPlayer target = actor.getServer().getPlayerList().getPlayer(UUID.fromString(intent.targetId()));
            if (target != null && ProgressOwnerService.resolve(target).isPresent()) sync(target);
        } catch (IllegalArgumentException ignored) { /* Invalid target identities have no client to synchronize. */ }
    }
    private static void sync(ServerPlayer target) {
        BrnQuestNetwork.syncProgress(target, false);
        target.inventoryMenu.broadcastFullState();
    }
}
