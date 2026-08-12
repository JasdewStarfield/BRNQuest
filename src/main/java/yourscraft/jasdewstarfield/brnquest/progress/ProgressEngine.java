package yourscraft.jasdewstarfield.brnquest.progress;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import yourscraft.jasdewstarfield.brnquest.api.OperationResult;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.data.RewardDefinition;
import yourscraft.jasdewstarfield.brnquest.data.TaskDefinition;
import yourscraft.jasdewstarfield.brnquest.event.QuestCompletedEvent;
import yourscraft.jasdewstarfield.brnquest.event.RewardClaimedEvent;
import yourscraft.jasdewstarfield.brnquest.network.BrnQuestNetwork;
import yourscraft.jasdewstarfield.brnquest.reward.RewardType;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypeRegistry;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;
import yourscraft.jasdewstarfield.brnquest.task.TaskType;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypeRegistry;

import java.util.*;

/** Server-thread transaction coordinator for personal progress and idempotent reward claims. */
public final class ProgressEngine {
    private static final ProgressEngine INSTANCE = new ProgressEngine();
    private static final int MAX_CHAIN_OPERATIONS = 256;
    private static final ThreadLocal<Integer> COMPLETION_DEPTH = ThreadLocal.withInitial(() -> 0);
    private final Map<UUID, Object> locks = new WeakHashMap<>();
    private ProgressEngine() {}
    public static ProgressEngine get() { return INSTANCE; }

    public PlayerProgress progress(ServerPlayer player) { return QuestProgressData.get(player.getServer()).get(player.getUUID()); }

    public void reconcile(ServerPlayer player) {
        var snapshot = QuestBookManager.get().active().orElse(null);
        if (snapshot == null) return;
        QuestProgressData data = QuestProgressData.get(player.getServer());
        PlayerProgress progress = data.get(player.getUUID());
        Set<String> current = new HashSet<>();
        for (QuestDefinition quest : snapshot.book().quests()) {
            current.add(quest.id().toString());
            if (progress.status(quest.id().toString()) == QuestStatus.LOCKED && dependenciesComplete(quest, progress)) {
                progress.status(quest.id().toString(), QuestStatus.AVAILABLE);
            }
        }
        progress.questsView().keySet().stream().filter(id -> !current.contains(id)).forEach(progress::orphan);
        progress.revision(snapshot.revision());
        data.setDirty();
    }

    public OperationResult complete(ServerPlayer player, ResourceLocation questId, boolean checkmarkIntent) {
        return boundedCompletion(() -> synchronizedPlayer(player, () -> {
            var snapshot = QuestBookManager.get().active().orElse(null);
            QuestDefinition quest = snapshot == null ? null : snapshot.quests().get(questId);
            if (quest == null) return OperationResult.failure("NOT_FOUND", "Unknown quest " + questId);
            QuestProgressData data = QuestProgressData.get(player.getServer());
            PlayerProgress progress = data.get(player.getUUID());
            QuestStatus status = progress.status(questId.toString());
            if (status == QuestStatus.COMPLETED || status == QuestStatus.REWARD_CLAIMED) return OperationResult.success("Quest already completed");
            if (!dependenciesComplete(quest, progress)) return OperationResult.failure("LOCKED", "Quest dependencies are incomplete");
            if (checkmarkIntent) quest.tasks().stream().filter(t -> t.typeId().getPath().equals("checkmark")).forEach(t -> progress.addTaskProgress(t.id().toString(), 1));
            for (TaskDefinition task : quest.tasks()) {
                TaskType<?> type = TaskTypeRegistry.get(task.typeId());
                if (type == null || (!task.optional() && !type.satisfied(player, task, progress))) return OperationResult.failure("UNSATISFIED", "Task is incomplete: " + task.id());
            }
            List<net.minecraft.world.item.ItemStack> inventoryBeforeConsume = player.getInventory().items.stream()
                    .map(net.minecraft.world.item.ItemStack::copy).toList();
            for (TaskDefinition task : quest.tasks()) {
                TaskType<?> type = TaskTypeRegistry.get(task.typeId());
                if (type != null && !type.consume(player, task)) {
                    restoreMainInventory(player, inventoryBeforeConsume);
                    return OperationResult.failure("CONSUME_FAILED", "Could not consume task items");
                }
            }
            return markCompleted(player, quest, progress, data);
        }));
    }

    public OperationResult forceComplete(ServerPlayer player, ResourceLocation questId) {
        return boundedCompletion(() -> synchronizedPlayer(player, () -> {
            var quest = QuestBookManager.get().active().map(s -> s.quests().get(questId)).orElse(null);
            if (quest == null) return OperationResult.failure("NOT_FOUND", "Unknown quest");
            QuestProgressData data = QuestProgressData.get(player.getServer());
            PlayerProgress progress = data.get(player.getUUID());
            QuestStatus status = progress.status(questId.toString());
            if (status == QuestStatus.COMPLETED || status == QuestStatus.REWARD_CLAIMED) return OperationResult.success("Quest already completed");
            // Administrative/API completion intentionally bypasses task resources;
            // callers have already made an explicit server-side authority decision.
            quest.tasks().forEach(task -> progress.addTaskProgress(task.id().toString(), Long.MAX_VALUE / 4));
            return markCompleted(player, quest, progress, data);
        }));
    }

    public OperationResult addTaskProgress(ServerPlayer player, ResourceLocation taskId, long amount) {
        if (amount <= 0) return OperationResult.failure("INVALID_AMOUNT", "Amount must be positive");
        boolean known = QuestBookManager.get().active().stream().flatMap(snapshot -> snapshot.book().quests().stream())
                .flatMap(quest -> quest.tasks().stream()).anyMatch(task -> task.id().equals(taskId));
        if (!known) return OperationResult.failure("NOT_FOUND", "Unknown task " + taskId);
        PlayerProgress progress = progress(player);
        progress.addTaskProgress(taskId.toString(), amount);
        QuestProgressData.get(player.getServer()).setDirty();
        BrnQuestNetwork.syncProgress(player, true);
        return OperationResult.success("Task progress updated");
    }

    public OperationResult claim(ServerPlayer player, ResourceLocation rewardId) {
        return synchronizedPlayer(player, () -> {
            var snapshot = QuestBookManager.get().active().orElse(null);
            if (snapshot == null) return OperationResult.failure("NO_BOOK", "No active book");
            QuestDefinition owner = snapshot.book().quests().stream().filter(q -> q.rewards().stream().anyMatch(r -> r.id().equals(rewardId))).findFirst().orElse(null);
            if (owner == null) return OperationResult.failure("NOT_FOUND", "Unknown reward");
            PlayerProgress progress = progress(player);
            if (progress.status(owner.id().toString()).ordinal() < QuestStatus.COMPLETED.ordinal()) return OperationResult.failure("LOCKED", "Quest is incomplete");
            if (progress.isClaimed(rewardId.toString())) return OperationResult.success("Reward already claimed");
            RewardDefinition reward = owner.rewards().stream().filter(r -> r.id().equals(rewardId)).findFirst().orElseThrow();
            RewardType<?> type = RewardTypeRegistry.get(reward.typeId());
            if (type == null) return OperationResult.failure("UNKNOWN_TYPE", "Unknown reward type");
            // The ledger is persisted before the non-repeatable side effect to prevent crash duplication.
            progress.claim(rewardId.toString());
            QuestProgressData data = QuestProgressData.get(player.getServer());
            data.setDirty();
            var result = type.execute(player, reward);
            if (!result.success()) return OperationResult.failure("EXECUTION_FAILED", result.message());
            if (owner.rewards().stream().allMatch(r -> progress.isClaimed(r.id().toString()))) progress.status(owner.id().toString(), QuestStatus.REWARD_CLAIMED);
            NeoForge.EVENT_BUS.post(new RewardClaimedEvent(player, snapshot.book().id(), rewardId));
            BrnQuestNetwork.syncProgress(player, true);
            return OperationResult.success(result.message());
        });
    }

    public void reset(ServerPlayer player, ResourceLocation questId) {
        var quest = QuestBookManager.get().active().map(s -> s.quests().get(questId)).orElse(null);
        if (quest == null) return;
        progress(player).resetQuest(questId.toString(), quest.tasks().stream().map(t -> t.id().toString()).toList(), quest.rewards().stream().map(r -> r.id().toString()).toList());
        QuestProgressData.get(player.getServer()).setDirty();
        reconcile(player);
    }

    public OperationResult toggleTracked(ServerPlayer player, ResourceLocation questId) {
        PlayerProgress progress = progress(player);
        QuestStatus status = progress.status(questId.toString());
        if (status != QuestStatus.AVAILABLE && status != QuestStatus.ACTIVE) return OperationResult.failure("NOT_TRACKABLE", "Quest is not available");
        progress.status(questId.toString(), status == QuestStatus.ACTIVE ? QuestStatus.AVAILABLE : QuestStatus.ACTIVE);
        QuestProgressData.get(player.getServer()).setDirty();
        BrnQuestNetwork.syncProgress(player, true);
        return OperationResult.success("Tracking updated");
    }

    private boolean dependenciesComplete(QuestDefinition quest, PlayerProgress progress) {
        return quest.dependencies().stream().allMatch(id -> {
            QuestStatus status = progress.status(id.toString());
            return status == QuestStatus.COMPLETED || status == QuestStatus.REWARD_CLAIMED;
        });
    }

    private OperationResult markCompleted(ServerPlayer player, QuestDefinition quest, PlayerProgress progress, QuestProgressData data) {
        progress.status(quest.id().toString(), QuestStatus.COMPLETED);
        progress.completedAt(quest.id().toString(), System.currentTimeMillis());
        data.setDirty();
        reconcile(player);
        NeoForge.EVENT_BUS.post(new QuestCompletedEvent(player, quest.bookId(), quest.id()));
        // Automatic and manual rewards enter the same idempotent claim ledger;
        // only the trigger differs.
        quest.rewards().stream().filter(reward -> reward.claimPolicy().equals("auto"))
                .forEach(reward -> claim(player, reward.id()));
        BrnQuestNetwork.syncProgress(player, true);
        return OperationResult.success("Quest completed");
    }

    private void restoreMainInventory(ServerPlayer player, List<net.minecraft.world.item.ItemStack> snapshot) {
        for (int index = 0; index < snapshot.size(); index++) player.getInventory().items.set(index, snapshot.get(index));
        player.getInventory().setChanged();
    }

    private OperationResult boundedCompletion(java.util.function.Supplier<OperationResult> operation) {
        int depth = COMPLETION_DEPTH.get();
        if (depth >= MAX_CHAIN_OPERATIONS) return OperationResult.failure("CHAIN_LIMIT", "Completion chain exceeded " + MAX_CHAIN_OPERATIONS + " operations");
        COMPLETION_DEPTH.set(depth + 1);
        try { return operation.get(); }
        finally { COMPLETION_DEPTH.set(depth); }
    }

    private <T> T synchronizedPlayer(ServerPlayer player, java.util.function.Supplier<T> operation) {
        Object lock;
        synchronized (locks) { lock = locks.computeIfAbsent(player.getUUID(), ignored -> new Object()); }
        synchronized (lock) { return operation.get(); }
    }
}
