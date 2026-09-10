package yourscraft.jasdewstarfield.brnquest.reward.table;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import yourscraft.jasdewstarfield.brnquest.reward.*;
import yourscraft.jasdewstarfield.brnquest.progress.ProgressEngine;
import yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerService;
import java.util.*;
import static yourscraft.jasdewstarfield.brnquest.reward.table.RewardTableJournal.*;

/** Nested table coordinator. Every continuation re-enters the authoritative owner/eligibility gate. */
public final class RewardTableService {
    private RewardTableService() {}
    private record Continuation(String key, ResourceLocation reward, ResourceLocation quest, int cycle, String generation) {}
    private static final Map<ServerPlayer, Map<String, Continuation>> QUEUED = new IdentityHashMap<>();
    private static final Map<Object, String> RESOURCE_TOKENS = new WeakHashMap<>();
    private static final Set<String> IN_FLIGHT = new HashSet<>();
    /** Scoped to the synchronous authoritative claim call, never retained across ticks or player requests. */
    private static final ThreadLocal<RewardTableChoice.Confirmation> CONFIRMATION = new ThreadLocal<>();
    private static final Map<String, ExecutionWindow> LAST_TICK = new HashMap<>();
    private static final Map<net.minecraft.server.MinecraftServer, PausedContinuationPump> PUMPS = new IdentityHashMap<>();

    /** Paused integrated servers keep processing tasks, but their game tick counter does not advance. */
    record ExecutionWindow(int tick, long pausedSlice) {
        static ExecutionWindow at(int tick, boolean paused, long nanos) {
            return new ExecutionWindow(tick, paused ? Math.floorDiv(nanos, 50_000_000L) : 0);
        }
    }
    public static RewardTableJournal journal(ServerPlayer player) {
        return new RewardTableJournal(player.server.getWorldPath(LevelResource.ROOT).resolve("data/brnquest-reward-tables"));
    }
    public static String key(RewardClaimContext context) {
        var root = context.rewardContext();
        return context.ownerId().providerId() + "/" + context.ownerId().ownerId() + "/" + root.bookId() + "/"
                + root.reward().id() + "/" + context.completionCycle() + "/" + context.claimGeneration() + "/"
                + (root.reward().teamReward() ? "shared" : root.player().getUUID());
    }
    public static RewardClaimResult claim(RewardClaimContext context) {
        return claim(context, () -> RewardTableTree.parse(context.rewardContext().reward().config().get("table")));
    }
    /** A standalone composable reward uses the same real root identity and durable leaf coordinator. */
    public static RewardClaimResult claimSingle(RewardClaimContext context) {
        return claim(context, () -> {
            var reward = context.rewardContext().reward();
            var entry = new com.google.gson.JsonObject();
            entry.addProperty("entry_id", "reward");
            entry.addProperty("type", reward.typeId().toString());
            var config = new com.google.gson.JsonObject();
            reward.config().forEach(config::addProperty);
            entry.add("config", config);
            var entries = new com.google.gson.JsonArray(); entries.add(entry);
            var table = new com.google.gson.JsonObject();
            table.addProperty("version", 1); table.addProperty("mode", "all"); table.add("entries", entries);
            return RewardTableTree.parse(table.toString());
        });
    }
    private static RewardClaimResult claim(RewardClaimContext context, java.util.function.Supplier<RewardTableTree> tree) {
        String key = key(context);
        if (!IN_FLIGHT.add(key)) return RewardClaimResult.pending("TABLE_EXECUTING", "Reward table already executing");
        try {
            var result = advance(context, key, tree);
            if (result.state() == RewardClaimResult.State.FAILURE) context.rewardContext().player().displayClientMessage(
                    net.minecraft.network.chat.Component.literal(result.code() + ": " + result.message()), false);
            return result;
        }
        catch (Exception error) {
            // Retain the exception class and stack: a path-only message hides storage failures from diagnosis.
            yourscraft.jasdewstarfield.brnquest.BRNQuest.LOGGER.error("Reward table claim blocked: {}", key, error);
            String detail = error.getClass().getSimpleName() + ": " + Objects.toString(error.getMessage(), "Table attempt failed closed");
            context.rewardContext().player().displayClientMessage(net.minecraft.network.chat.Component.literal("TABLE_BLOCKED: " + detail), false);
            return RewardClaimResult.failure("TABLE_BLOCKED", detail);
        }
        finally {
            IN_FLIGHT.remove(key);
            // Player ticks normally send these vanilla updates. Explicit claims must also refresh a paused screen.
            var player = context.rewardContext().player();
            player.inventoryMenu.broadcastFullState();
            if (player.connection != null) player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetExperiencePacket(
                    player.experienceProgress, player.totalExperience, player.experienceLevel));
        }
    }
    private static RewardClaimResult advance(RewardClaimContext context, String key, java.util.function.Supplier<RewardTableTree> source) throws Exception {
        var player = context.rewardContext().player();
        var journal = journal(player);
        Attempt attempt = journal.read(key);
        if (attempt == null) {
            if (CONFIRMATION.get() != null) throw new IllegalArgumentException("Choice attempt no longer exists for this owner/cycle");
            var tree = source.get();
            String id = UUID.randomUUID().toString();
            var snapshot = tree.document();
            preflight(snapshot, "root", context, id);
            snapshot.addProperty("brnquest.root_quest", context.rewardContext().questId().toString());
            List<Leaf> leaves = new ArrayList<>();
            var plan = RewardTablePlan.create(RewardTableTree.parse(snapshot.toString()),
                    RewardTableDraws.tickets(java.util.concurrent.ThreadLocalRandom.current()),
                    (entry,path,occurrence) -> prepare(context,id,entry,path,occurrence),leaves);
            snapshot.add(RewardTablePlan.FIELD,plan);
            attempt = new Attempt(2,key,id,player.getUUID().toString(),snapshot.toString(),resources(player),
                    State.READY,leaves,System.currentTimeMillis(),"All branches prepared without effects");
            if (RewardTablePlan.pending(attempt)!=null) attempt=attempt.state(State.AWAITING_CHOICE,"Awaiting all choices");
            journal.begin(attempt); // Forced storage includes empty draws before any visible effect or receipt.
        }
        if (!attempt.executor().equals(player.getUUID().toString())) return RewardClaimResult.pending("TABLE_BOUND_EXECUTOR", "Bound executor=" + attempt.executor());
        if (attempt.state() == State.SUCCEEDED) return RewardClaimResult.success("Reward table receipt reconciled without replay");
        var frozen = com.google.gson.JsonParser.parseString(attempt.snapshot()).getAsJsonObject();
        if (!frozen.has("brnquest.root_quest") || !frozen.get("brnquest.root_quest").getAsString().equals(context.rewardContext().questId().toString())) {
            journal.save(attempt.state(State.STALE, "Root reward moved to another quest"));
            return RewardClaimResult.failure("TABLE_STALE", "Root reward moved to another quest");
        }
        if (attempt.state() == State.NEEDS_REVIEW || attempt.state() == State.STALE)
            return RewardClaimResult.failure("TABLE_" + attempt.state(), attempt.detail() + " attempt=" + attempt.attemptId());
        // Native resource contents and explicit adapter versions must still match the frozen preparation.
        if (!attempt.resources().equals(resources(player))) {
            journal.save(attempt.state(State.BLOCKED, "Server resources changed; review prepared snapshot before continuing"));
            return RewardClaimResult.failure("TABLE_RESOURCES_CHANGED", "Prepared resources changed; attempt=" + attempt.attemptId());
        }
        if (CONFIRMATION.get() != null) {
            String attemptId=attempt.attemptId();
            var selected = RewardTablePlan.select(attempt, CONFIRMATION.get(),
                    RewardTableDraws.tickets(java.util.concurrent.ThreadLocalRandom.current()),
                    (entry,path,occurrence) -> prepare(context,attemptId,entry,path,occurrence));
            if (selected != attempt) journal.save(selected); // Freeze the decision before executing the selected leaf.
            attempt = selected;
            CONFIRMATION.remove(); // Nested completion events must not inherit this root's confirmation.
        }
        if (attempt.state() == State.AWAITING_CHOICE)
            return RewardClaimResult.pending("TABLE_AWAITING_CHOICE", "Select one reward and confirm; closing suspends this attempt");
        // A resource-blocked or malformed unconfirmed choice must never fall through to its candidate leaves.
        if (attempt.version()==2 && RewardTablePlan.pending(attempt)!=null)
            return RewardClaimResult.failure("TABLE_CHOICE_BLOCKED", "Unconfirmed nested choice requires review");
        if (attempt.version()==1 && frozen.has("mode") && frozen.get("mode").getAsString().equals("choice")
                && !com.google.gson.JsonParser.parseString(attempt.snapshot()).getAsJsonObject().has("brnquest.selected"))
            return RewardClaimResult.failure("TABLE_CHOICE_BLOCKED", "Unconfirmed choice requires review before continuing");
        var window = ExecutionWindow.at(player.server.getTickCount(), player.server.isPaused(), System.nanoTime());
        if (Objects.equals(LAST_TICK.get(key), window)) {
            queue(context, key);
            return RewardClaimResult.pending("TABLE_EXECUTING", "Execution continues next tick");
        }
        LAST_TICK.put(key, window);
        int executed = 0;
        for (int i = 0; i < attempt.leaves().size(); i++) {
            if (!stillEligible(context)) {
                journal.save(attempt.state(State.STALE, "Root owner or completion cycle changed during execution"));
                return RewardClaimResult.failure("TABLE_STALE", "Root context changed; effects retained without replay");
            }
            Leaf leaf = attempt.leaves().get(i);
            if (leaf.state() == LeafState.SUCCEEDED || leaf.state() == LeafState.ACKNOWLEDGED) continue;
            var type = RewardTypeRegistry.get(ResourceLocation.parse(leaf.type()));
            if (type == null || type.composition().isEmpty()) throw new IllegalArgumentException(leaf.path() + ": adapter unavailable");
            var adapter = type.composition().orElseThrow();
            if (!adapter.version().equals(leaf.adapterVersion())) throw new IllegalArgumentException(leaf.path() + ": adapter changed");
            var leafContext = new RewardLeafContext(context, leaf.path(), leaf.occurrence(), ResourceLocation.parse(leaf.type()), leaf.config());
            RewardClaimResult result;
            if (leaf.state() == LeafState.NOT_STARTED) {
                // Recheck resources and current permissions immediately before each side effect.
                try { adapter.prepare(leafContext); }
                catch (Exception error) {
                    journal.save(attempt.leaf(i, leaf.state(LeafState.FAILED_NO_EFFECT, Objects.toString(error.getMessage(), "Preflight failed")))
                            .state(State.NEEDS_REVIEW, leaf.path() + ": preflight failed without effects"));
                    return RewardClaimResult.failure("TABLE_PREFLIGHT_FAILED", leaf.path());
                }
                if (executed++ >= 8) { queue(context, key); return RewardClaimResult.pending("TABLE_EXECUTING", "Execution continues next tick"); }
                attempt = attempt.leaf(i, leaf.state(LeafState.STARTED, "Forced intent before execution")).state(State.EXECUTING, leaf.path());
                journal.save(attempt);
                leaf = attempt.leaves().get(i);
                try { result = adapter.execute(leafContext, leaf.prepared()); }
                catch (Exception error) { result = RewardClaimResult.failure("UNKNOWN", Objects.toString(error.getMessage(), "Execution threw")); }
            } else {
                result = adapter.recover(leafContext, leaf.prepared());
            }
            if (result.state() == RewardClaimResult.State.SUCCESS) {
                attempt = attempt.leaf(i, leaf.state(LeafState.SUCCEEDED, result.message()));
                journal.save(attempt);
            } else if (result.state() == RewardClaimResult.State.PENDING) {
                journal.save(attempt.leaf(i, leaf.state(LeafState.PENDING, result.message())));
                queue(context, key);
                return result;
            } else {
                journal.save(attempt.leaf(i, leaf.state(LeafState.UNKNOWN, result.message())).state(State.NEEDS_REVIEW, leaf.path() + ": " + result.message()));
                return RewardClaimResult.failure("TABLE_NEEDS_REVIEW", leaf.path() + "; attempt=" + attempt.attemptId());
            }
        }
        if (!stillEligible(context)) {
            journal.save(attempt.state(State.STALE, "Root changed before final receipt"));
            return RewardClaimResult.failure("TABLE_STALE", "Root changed before final receipt");
        }
        journal.save(attempt.state(State.SUCCEEDED, "Every leaf consumed; normal root receipt may now be committed"));
        LAST_TICK.remove(key);
        return RewardClaimResult.success("Reward table delivered");
    }
    /** Normalize and prepare every configured branch, including choices that will never be selected. */
    private static void preflight(com.google.gson.JsonObject table,String parent,RewardClaimContext context,String id) {
        for(var value:table.getAsJsonArray("entries")) {
            var entry=value.getAsJsonObject(); String path=parent+"/"+entry.get("entry_id").getAsString();
            if(entry.has("table")) { preflight(entry.getAsJsonObject("table"),path,context,id); continue; }
            var type=RewardTypeRegistry.get(ResourceLocation.parse(entry.get("type").getAsString()));
            if(type==null || type.composition().isEmpty()) throw new IllegalArgumentException(path+": unsupported composition");
            var config=type.normalizeConfig(RewardTableTree.config(entry));
            yourscraft.jasdewstarfield.brnquest.data.StringMapConfigCodec.decode(type.configCodec(),config).getOrThrow();
            var normalized=new com.google.gson.JsonObject();config.forEach(normalized::addProperty);entry.add("config",normalized);
            try {
                // Validate all candidates without drawing loot for an unselected or repeatedly checked branch.
                type.composition().orElseThrow().prepare(new RewardLeafContext(context,path,id+"/"+path+"/preflight",
                        ResourceLocation.parse(entry.get("type").getAsString()),config));
            } catch (Exception error) { throw new IllegalArgumentException(path+": "+error.getMessage(),error); }
        }
    }
    private static Leaf prepare(RewardClaimContext context,String id,com.google.gson.JsonObject entry,String path,String occurrence) {
        try {
            var typeId=ResourceLocation.parse(entry.get("type").getAsString());
            var adapter=RewardTypeRegistry.get(typeId).composition().orElseThrow();
            var config=RewardTableTree.config(entry);
            var leafContext=new RewardLeafContext(context,path,id+"/"+occurrence,typeId,config);
            var prepared=adapter.freeze(leafContext);
            return new Leaf(path,leafContext.occurrenceId(),typeId.toString(),config,prepared,adapter.version(),
                    LeafState.NOT_STARTED,0,0,System.currentTimeMillis(),"Prepared without effects");
        } catch(Exception error) { throw new IllegalArgumentException(path+": "+error.getMessage(),error); }
    }
    private static String resources(ServerPlayer player) throws Exception {
        Object identity = player.server.getServerResources();
        String cached = RESOURCE_TOKENS.get(identity);
        if (cached != null) return cached;
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        long bytes = 0;
        // Hash effective gameplay resources, not the editable task book. Identical server restarts remain resumable.
        var resources = new TreeMap<ResourceLocation, net.minecraft.server.packs.resources.Resource>();
        // Pack implementations differ in how they handle an empty prefix; enumerate concrete roots explicitly.
        for (String directory : List.of("function", "advancement", "tags", "brnquest/target_groups", "loot_table", "predicate", "item_modifier"))
            resources.putAll(player.server.getResourceManager().listResources(directory, id -> true));
        for (var entry : resources.entrySet()) {
            digest.update(entry.getKey().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update((byte) 0);
            try (var input = entry.getValue().open()) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) >= 0) {
                    bytes += count;
                    if (bytes > 32L * 1024 * 1024) throw new IllegalArgumentException("Gameplay resource verification exceeds 32 MiB");
                    digest.update(buffer, 0, count);
                }
            }
            digest.update((byte) 0);
        }
        String token = HexFormat.of().formatHex(digest.digest());
        RESOURCE_TOKENS.put(identity, token);
        return token;
    }
    private static boolean stillEligible(RewardClaimContext context) {
        var root = context.rewardContext();
        var progress = ProgressEngine.get().progress(root.player());
        return ProgressOwnerService.require(root.player()).equals(context.ownerId())
                && progress.completionCycles(root.questId().toString()) == context.completionCycle()
                && progress.claimGeneration(root.questId().toString()).equals(context.claimGeneration())
                && progress.status(root.questId().toString()).ordinal() >= yourscraft.jasdewstarfield.brnquest.progress.QuestStatus.COMPLETED.ordinal()
                && yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager.get().active().map(s -> s.book().id().equals(root.bookId())).orElse(false);
    }
    /** Resolve current ownership instead of accepting owner, cycle, reward configuration or a journal key from the client. */
    public static RewardClaimContext choiceContext(ServerPlayer player, ResourceLocation id) {
        var snapshot = yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager.get().active().orElseThrow();
        for (var quest : snapshot.book().quests()) for (var reward : quest.rewards()) {
            if (!reward.id().equals(id) || !reward.typeId().equals(RewardTableReward.ID)) continue;
            var progress = ProgressEngine.get().progress(player);
            var owner = ProgressOwnerService.require(player);
            var context = new RewardClaimContext(new RewardContext(player, quest.bookId(), quest.id(),
                    yourscraft.jasdewstarfield.brnquest.api.ApiViews.reward(reward)), owner,
                    progress.completionCycles(quest.id().toString()), progress.claimGeneration(quest.id().toString()));
            if (!stillEligible(context)) throw new IllegalArgumentException("Root reward is no longer eligible");
            if (!owner.providerId().equals(yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerProviders.PERSONAL)
                    && !progress.completionMembers(quest.id().toString()).contains(player.getUUID()))
                throw new IllegalArgumentException("Player was not a member when this cycle completed");
            // The core repeats membership checks while holding its owner lock before any selection is saved.
            return context;
        }
        throw new IllegalArgumentException("Root reward no longer exists");
    }
    public static yourscraft.jasdewstarfield.brnquest.api.OperationResult confirmChoice(ServerPlayer player, ResourceLocation reward,
            RewardTableChoice.Confirmation confirmation) throws Exception {
        var context = choiceContext(player, reward);
        var attempt = journal(player).read(key(context));
        if (attempt == null || !attempt.executor().equals(player.getUUID().toString()))
            throw new IllegalArgumentException("Choice is missing or bound to another player");
        RewardTablePlan.validate(attempt, confirmation); // Also reject conflicting retries after the core receipt was committed.
        CONFIRMATION.set(confirmation);
        try { return ProgressEngine.get().claim(player, reward); }
        finally { CONFIRMATION.remove(); }
    }
    private static void queue(RewardClaimContext context, String key) {
        var root = context.rewardContext();
        QUEUED.computeIfAbsent(root.player(), ignored -> new LinkedHashMap<>()).put(key,
                new Continuation(key, root.reward().id(), root.questId(), context.completionCycle(), context.claimGeneration()));
        schedulePump(root.player().server);
    }

    /** Only the wake-up is delayed off-thread; journal, effects and owner checks always run on the server thread. */
    private static void schedulePump(net.minecraft.server.MinecraftServer server) {
        PUMPS.computeIfAbsent(server, ignored -> new PausedContinuationPump(server::isPaused,
                () -> QUEUED.keySet().stream().anyMatch(player -> player.server == server),
                () -> {
                    for (var player : List.copyOf(QUEUED.keySet())) if (player.server == server) tick(player);
                }, work -> {
                    int scheduledTick = server.getTickCount();
                    java.util.concurrent.CompletableFuture.delayedExecutor(50, java.util.concurrent.TimeUnit.MILLISECONDS)
                            .execute(() -> server.tell(new net.minecraft.server.TickTask(scheduledTick, work)));
                })).request();
    }
    public static void tick(ServerPlayer player) {
        var pendingList = QUEUED.remove(player);
        if (pendingList == null) return;
        for (var pending : pendingList.values()) resume(player, pending);
    }
    private static void resume(ServerPlayer player, Continuation pending) {
        var progress = ProgressEngine.get().progress(player);
        var snapshot = yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager.get().active().orElse(null);
        if (snapshot == null) { markStale(player, pending); return; }
        var quest = snapshot.quests().get(pending.quest());
        if (quest == null) { markStale(player, pending); return; }
        var reward = quest.rewards().stream().filter(r -> r.id().equals(pending.reward())).findFirst().orElse(null);
        if (reward == null) { markStale(player, pending); return; }
        var current = new RewardClaimContext(new RewardContext(player, quest.bookId(), quest.id(), yourscraft.jasdewstarfield.brnquest.api.ApiViews.reward(reward)),
                ProgressOwnerService.require(player), progress.completionCycles(quest.id().toString()), progress.claimGeneration(quest.id().toString()));
        if (!key(current).equals(pending.key())) { markStale(player, pending); return; }
        ProgressEngine.get().claim(player, pending.reward());
    }
    private static void markStale(ServerPlayer player, Continuation pending) {
        try {
            var journal = journal(player); var attempt = journal.read(pending.key());
            if (attempt != null && attempt.state() != State.SUCCEEDED)
                journal.save(attempt.state(State.STALE, "Queued root no longer matches current owner, book or cycle"));
        } catch (Exception error) {
            yourscraft.jasdewstarfield.brnquest.BRNQuest.LOGGER.error("Could not record stale reward table {}", pending.key(), error);
        }
    }
    public static void logout(ServerPlayer player) { QUEUED.remove(player); }
    /** Only a leaf whose preparation failed before STARTED can be explicitly retried. */
    public static void retryNoEffect(RewardClaimContext context, String attemptId, String occurrence, String actor) throws Exception {
        var journal = journal(context.rewardContext().player()); String key = key(context); var attempt = journal.read(key);
        if (attempt == null || !attempt.attemptId().equals(attemptId) || !attempt.executor().equals(context.rewardContext().player().getUUID().toString())
                || !stillEligible(context)) throw new IllegalArgumentException("Attempt changed or executor is ineligible");
        if (!attempt.resources().equals(resources(context.rewardContext().player()))) throw new IllegalArgumentException("Prepared resources changed");
        for (int i = 0; i < attempt.leaves().size(); i++) {
            var leaf = attempt.leaves().get(i);
            if (!leaf.occurrence().equals(occurrence)) continue;
            if (leaf.state() != LeafState.FAILED_NO_EFFECT) throw new IllegalArgumentException("Only FAILED_NO_EFFECT permits retry");
            var adapter = RewardTypeRegistry.get(ResourceLocation.parse(leaf.type())).composition().orElseThrow();
            if (!adapter.version().equals(leaf.adapterVersion())) throw new IllegalArgumentException("Adapter changed");
            adapter.prepare(new RewardLeafContext(context,leaf.path(),leaf.occurrence(),ResourceLocation.parse(leaf.type()),leaf.config()));
            journal.save(attempt.leaf(i,leaf.state(LeafState.NOT_STARTED,"Retry authorized by " + actor + "; previous=" + leaf.detail()))
                    .state(State.READY,"No-effect retry passed preparation"));
            return;
        }
        throw new IllegalArgumentException("Unknown occurrence");
    }
    public static void clear() {
        QUEUED.clear(); LAST_TICK.clear(); IN_FLIGHT.clear(); RESOURCE_TOKENS.clear();
        PUMPS.values().forEach(PausedContinuationPump::cancel);
        PUMPS.clear();
    }
}
