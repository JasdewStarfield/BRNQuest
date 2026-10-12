package yourscraft.jasdewstarfield.brnquest.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import yourscraft.jasdewstarfield.brnquest.event.*;
import yourscraft.jasdewstarfield.brnquest.data.*;
import yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerService;
import yourscraft.jasdewstarfield.brnquest.progress.ProgressEngine;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;
import yourscraft.jasdewstarfield.brnquest.task.*;
import java.util.UUID;
import java.util.*;
import yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerId;
import yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerView;

/** Observes committed transitions only; snapshot synchronization never produces a notification. */
public final class QuestNotificationService {
    private static boolean registered;
    private record Delivery(BrnQuestNetwork.QuestNotificationPayload payload, ProgressOwnerId owner, Set<UUID> recipients) {}
    private static final List<Delivery> PENDING = new ArrayList<>();
    private static MinecraftServer pendingServer;
    private static boolean flushScheduled;
    private QuestNotificationService() {}
    public static synchronized void register() {
        if (registered) return;
        registered = true;
        BrnQuestEvents.subscribe(QuestCompletedEvent.class, event -> {
            var player = player(event.playerId());
            var quest = quest(event.bookId(), event.questId());
            if (player != null && quest != null && QuestNotificationPolicy.read(quest.extensions()).questCompleted())
                send(player, quest, "quest", "", true);
        });
        BrnQuestEvents.subscribe(TaskProgressChangedEvent.class, event -> {
            var player = player(event.playerId());
            var quest = quest(event.bookId(), event.questId());
            if (player == null || quest == null || event.currentValue() <= event.previousValue()
                    || !QuestNotificationPolicy.read(quest.extensions()).taskCompleted()) return;
            var type = TaskTypeRegistry.get(event.task().typeId());
            if (type == null) return;
            var before = new TaskContext(player, event.bookId(), event.questId(), event.task(), event.previousValue());
            var after = new TaskContext(player, event.bookId(), event.questId(), event.task(), event.currentValue());
            if (crossedCompletion(type, before, after))
                send(player, quest, "task", event.taskId().toString(), !quest.behavior().requireAllTeamMembers());
        });
        BrnQuestEvents.subscribe(RewardClaimedEvent.class, event -> {
            var player = player(event.playerId());
            var quest = quest(event.bookId(), event.questId());
            if (player != null && quest != null && QuestNotificationPolicy.read(quest.extensions()).rewardClaimed())
                send(player, quest, "reward", event.rewardId().toString(), false);
        });
        BrnQuestEvents.subscribe(ProgressOwnerChangedEvent.class, event -> {
            var player = player(event.playerId());
            if (player != null) clear(player);
        });
        BrnQuestEvents.subscribe(QuestBookReloadedEvent.class, event -> {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server != null && server.isSameThread()) {
                PENDING.clear();
                server.getPlayerList().getPlayers().forEach(QuestNotificationService::clear);
            }
        });
    }
    private static ServerPlayer player(UUID id) {
        var server = ServerLifecycleHooks.getCurrentServer();
        return server == null || !server.isSameThread() ? null : server.getPlayerList().getPlayer(id);
    }
    /** Threshold crossing uses the type contract, so partial counters and resets cannot masquerade as completion. */
    static boolean crossedCompletion(TaskType<?> type, TaskContext before, TaskContext after) {
        return after.progress() > before.progress() && !TaskTypeExecutor.satisfied(type, before)
                && TaskTypeExecutor.satisfied(type, after);
    }
    private static QuestDefinition quest(ResourceLocation bookId, ResourceLocation id) {
        return QuestBookManager.get().active().filter(book -> book.book().id().equals(bookId))
                .map(book -> book.quests().get(id)).orElse(null);
    }
    private static void clear(ServerPlayer player) {
        if (supports(player)) PacketDistributor.sendToPlayer(player, new BrnQuestNetwork.QuestNotificationPayload(UUID.randomUUID().toString(),
                "", "", "", "", "clear"));
    }
    /** The optional channel must not send unknown packets to clients that have not negotiated it. */
    private static boolean supports(ServerPlayer player) {
        return net.neoforged.neoforge.network.registration.NetworkRegistry.hasChannel(player.connection, BrnQuestNetwork.QuestNotificationPayload.TYPE.id());
    }
    private static void send(ServerPlayer actor, QuestDefinition quest, String kind, String target, boolean shared) {
        var snapshot = QuestBookManager.get().active().orElse(null);
        var owner = ProgressOwnerService.resolve(actor).orElse(null);
        if (snapshot == null || owner == null || !snapshot.book().id().equals(quest.bookId())) return;
        var payload = new BrnQuestNetwork.QuestNotificationPayload(UUID.randomUUID().toString(), quest.bookId().toString(),
                snapshot.revision(), quest.id().toString(), target, kind);
        if (pendingServer != actor.server) { PENDING.clear(); pendingServer = actor.server; flushScheduled = false; }
        if (PENDING.size() < 2048) PENDING.add(new Delivery(payload, owner.id(), shared ? Set.copyOf(owner.members()) : Set.of(actor.getUUID())));
        if (!flushScheduled) {
            flushScheduled = true;
            var server = actor.server;
            // tell always queues instead of executing inline inside the owner transaction. The normal
            // server task loop also runs while an integrated server is paused by a menu.
            server.tell(new TickTask(server.getTickCount(), () -> flush(server)));
        }
    }
    private static void flush(MinecraftServer server) {
        if (pendingServer != server) return;
        var deliveries = List.copyOf(PENDING);
        PENDING.clear(); flushScheduled = false;
        var snapshot = QuestBookManager.get().active().orElse(null);
        if (snapshot == null) return;
        var owners = new HashMap<UUID, Optional<ProgressOwnerView>>();
        var visibility = new HashMap<UUID, Set<String>>();
        for (var delivery : deliveries) {
            var payload = delivery.payload();
            if (!payload.bookId().equals(snapshot.book().id().toString()) || !payload.revision().equals(snapshot.revision())) continue;
            for (UUID id : delivery.recipients()) {
                var recipient = server.getPlayerList().getPlayer(id);
                if (recipient == null || !supports(recipient)) continue;
                var owner = owners.computeIfAbsent(id, ignored -> ProgressOwnerService.resolve(recipient));
                if (owner.isEmpty() || !owner.get().id().equals(delivery.owner())) continue;
                // Compute visibility once per recipient for an entire dependency-completion chain.
                if (visibility.computeIfAbsent(id, ignored -> ProgressEngine.get().visibleQuestIds(recipient)).contains(payload.questId()))
                    PacketDistributor.sendToPlayer(recipient, payload);
            }
        }
    }
    public static void stopped(MinecraftServer server) {
        if (pendingServer == server) { PENDING.clear(); pendingServer = null; flushScheduled = false; }
    }
}
