package yourscraft.jasdewstarfield.brnquest.progress;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.api.ApiViews;
import yourscraft.jasdewstarfield.brnquest.api.BrnQuestApi;
import yourscraft.jasdewstarfield.brnquest.api.OperationResult;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.data.RewardDefinition;
import yourscraft.jasdewstarfield.brnquest.data.TaskDefinition;
import yourscraft.jasdewstarfield.brnquest.event.QuestCompletedEvent;
import yourscraft.jasdewstarfield.brnquest.event.RewardClaimedEvent;
import yourscraft.jasdewstarfield.brnquest.event.BrnQuestEvents;
import yourscraft.jasdewstarfield.brnquest.event.TaskProgressChangedEvent;
import yourscraft.jasdewstarfield.brnquest.network.BrnQuestNetwork;
import yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerId;
import yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerService;
import yourscraft.jasdewstarfield.brnquest.reward.RewardType;
import yourscraft.jasdewstarfield.brnquest.reward.RewardContext;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypeExecutor;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypeRegistry;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;
import yourscraft.jasdewstarfield.brnquest.task.TaskType;
import yourscraft.jasdewstarfield.brnquest.task.TaskContext;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypeExecutor;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypeRegistry;
import yourscraft.jasdewstarfield.brnquest.task.TaskSubmissionSelection;

import java.util.*;

/** Server-thread transaction coordinator for owner-scoped progress and idempotent reward claims. */
public final class ProgressEngine {
    private static final ProgressEngine INSTANCE = new ProgressEngine();
    private static final int MAX_CHAIN_OPERATIONS = 256;
    private static final ThreadLocal<Integer> COMPLETION_DEPTH = ThreadLocal.withInitial(() -> 0);
    private final Map<ProgressOwnerId, Object> locks = new WeakHashMap<>();
    private ProgressEngine() {}
    public static ProgressEngine get() { return INSTANCE; }

    public PlayerProgress progress(ServerPlayer player) {
        return QuestProgressData.get(player.getServer()).get(ProgressOwnerService.require(player));
    }

    public void reconcile(ServerPlayer player) {
        var snapshot = QuestBookManager.get().active().orElse(null);
        if (snapshot == null) return;
        QuestProgressData data = QuestProgressData.get(player.getServer());
        PlayerProgress progress = data.get(ProgressOwnerService.require(player));
        // Canonical quest renames are published as aliases; migrate quest-level state before
        // availability and orphan checks so an intentional rename does not reset player progress.
        reconcileLegacyIds(snapshot.book(), progress);
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

    private static ResourceLocation typedLegacyId(String key, String prefix) {
        return key.startsWith(prefix) ? ResourceLocation.tryParse(key.substring(prefix.length())) : null;
    }

    /** Applies unambiguous ledger aliases while preserving any source ID that is live again. */
    static void reconcileLegacyIds(QuestBookDefinition book, PlayerProgress progress) {
        Set<ResourceLocation> questIds = book.quests().stream().map(QuestDefinition::id)
                .collect(java.util.stream.Collectors.toSet());
        Set<ResourceLocation> taskIds = book.quests().stream().flatMap(quest -> quest.tasks().stream())
                .map(TaskDefinition::id).collect(java.util.stream.Collectors.toSet());
        Set<ResourceLocation> rewardIds = book.quests().stream().flatMap(quest -> quest.rewards().stream())
                .map(RewardDefinition::id).collect(java.util.stream.Collectors.toSet());
        book.legacyIds().forEach((oldId, newId) -> {
            ResourceLocation oldTaskId = typedLegacyId(oldId, "@task:");
            ResourceLocation oldRewardId = typedLegacyId(oldId, "@reward:");
            if (oldTaskId != null && taskIds.contains(newId) && !taskIds.contains(oldTaskId)) {
                progress.migrateTaskId(oldTaskId.toString(), newId.toString());
            } else if (oldRewardId != null && rewardIds.contains(newId) && !rewardIds.contains(oldRewardId)) {
                progress.migrateRewardId(oldRewardId.toString(), newId.toString());
            } else if (!oldId.startsWith("@task:") && !oldId.startsWith("@reward:") && questIds.contains(newId)) {
                ResourceLocation oldQuestId = ResourceLocation.tryParse(oldId);
                if (oldQuestId == null || !questIds.contains(oldQuestId)) {
                    progress.migrateQuestId(oldId, newId.toString());
                }
            }
        });
    }

    /** Reconciles every connected player after a successfully installed task-book revision. */
    public void reconcileOnlinePlayers(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            reconcile(player);
            // A reload can change both definitions and status availability, so send a full
            // definition/progress pair instead of relying on an incremental progress delta.
            BrnQuestNetwork.syncAll(player, false);
        }
    }

    public OperationResult complete(ServerPlayer player, ResourceLocation questId, boolean checkmarkIntent) {
        return boundedCompletion(() -> synchronizedOwner(player, () -> {
            var snapshot = QuestBookManager.get().active().orElse(null);
            QuestDefinition quest = snapshot == null ? null : snapshot.quests().get(questId);
            if (quest == null) return OperationResult.failure("NOT_FOUND", "Unknown quest " + questId);
            QuestProgressData data = QuestProgressData.get(player.getServer());
            PlayerProgress progress = data.get(ProgressOwnerService.require(player));
            QuestStatus status = progress.status(questId.toString());
            if (status == QuestStatus.COMPLETED || status == QuestStatus.REWARD_CLAIMED) return OperationResult.noChange("ALREADY_COMPLETED", "Quest already completed");
            if (!dependenciesComplete(quest, progress)) return OperationResult.failure("LOCKED", "Quest dependencies are incomplete");
            if (checkmarkIntent) {
                // Quest-wide completion remains a generic intent; each task type decides whether it accepts it.
                quest.tasks().forEach(task -> {
                    TaskType<?> type = TaskTypeRegistry.get(task.typeId());
                    TaskContext context = taskContext(player, quest, task, progress);
                    if (type != null && TaskTypeExecutor.acceptsQuestCompletionIntent(type, context.task())
                            && TaskTypeExecutor.submit(type, context).success()) {
                        changeTaskProgress(player, quest, task, progress, 1);
                    }
                });
            }
            for (TaskDefinition task : quest.tasks()) {
                TaskType<?> type = TaskTypeRegistry.get(task.typeId());
                if (type == null || (!task.optional() && !TaskTypeExecutor.satisfied(type,
                        taskContext(player, quest, task, progress)))) {
                    return OperationResult.failure("UNSATISFIED", "Task is incomplete: " + task.id());
                }
            }
            List<net.minecraft.world.item.ItemStack> inventoryBeforeConsume = player.getInventory().items.stream()
                    .map(net.minecraft.world.item.ItemStack::copy).toList();
            for (TaskDefinition task : quest.tasks()) {
                TaskType<?> type = TaskTypeRegistry.get(task.typeId());
                // A row submitted earlier has already applied its one-time consumption.
                if (type != null && progress.taskProgress(task.id().toString()) < 1
                        && !TaskTypeExecutor.consume(type, taskContext(player, quest, task, progress))) {
                    restoreMainInventory(player, inventoryBeforeConsume);
                    return OperationResult.failure("CONSUME_FAILED", "Could not consume task items");
                }
            }
            return markCompleted(player, quest, progress, data);
        }));
    }

    /** Applies one task-row intent, then lets the normal transaction decide whether the quest can finish. */
    public OperationResult completeTask(ServerPlayer player, ResourceLocation questId, ResourceLocation taskId) {
        return completeTask(player, questId, taskId, TaskSubmissionSelection.AUTOMATIC);
    }

    /** Applies a bounded child-entry selection that the task type revalidates inside the owner lock. */
    public OperationResult completeTask(ServerPlayer player, ResourceLocation questId, ResourceLocation taskId,
                                        TaskSubmissionSelection selection) {
        OperationResult result = synchronizedOwner(player, () -> {
            var snapshot = QuestBookManager.get().active().orElse(null);
            QuestDefinition quest = snapshot == null ? null : snapshot.quests().get(questId);
            if (quest == null) return OperationResult.failure("NOT_FOUND", "Unknown quest " + questId);
            TaskDefinition task = quest.tasks().stream().filter(candidate -> candidate.id().equals(taskId)).findFirst().orElse(null);
            if (task == null) return OperationResult.failure("NOT_FOUND", "Task does not belong to quest " + taskId);
            PlayerProgress progress = progress(player);
            // Submission is an idempotent command. Check the per-task ledger before quest
            // status so a retransmission after quest completion is also a successful no-op.
            if (progress.taskProgress(taskId.toString()) >= 1) {
                return OperationResult.noChange("ALREADY_SUBMITTED", "Task already submitted");
            }
            QuestStatus status = progress.status(questId.toString());
            if (status != QuestStatus.AVAILABLE && status != QuestStatus.ACTIVE) {
                return OperationResult.failure("LOCKED", "Quest is not available");
            }
            if (!dependenciesComplete(quest, progress)) return OperationResult.failure("LOCKED", "Quest dependencies are incomplete");

            TaskType<?> type = TaskTypeRegistry.get(task.typeId());
            if (type == null) return OperationResult.failure("UNKNOWN_TYPE", "Unknown task type " + task.typeId());
            TaskContext context = taskContext(player, quest, task, progress);
            if (!TaskTypeExecutor.allowsManualSubmission(type, context.task())) {
                return OperationResult.failure("NOT_SUBMITTABLE", "Task does not accept manual submission: " + task.id());
            }
            List<net.minecraft.world.item.ItemStack> inventoryBeforeSubmit = player.getInventory().items.stream()
                    .map(net.minecraft.world.item.ItemStack::copy).toList();
            var submission = TaskTypeExecutor.submit(type, context, selection);
            if (!submission.success()) {
                restoreMainInventory(player, inventoryBeforeSubmit);
                return OperationResult.failure(submission.code(), submission.message() + ": " + task.id());
            }
            changeTaskProgress(player, quest, task, progress, 1);
            QuestProgressData.get(player.getServer()).setDirty();

            return complete(player, questId, false);
        });
        // A response is sent for both accepted and rejected intents so the client can clear
        // its pending-click guard. Full inventory state avoids stale counts on paused screens.
        BrnQuestNetwork.syncProgress(player, true);
        player.inventoryMenu.broadcastFullState();
        return result;
    }

    public OperationResult forceComplete(ServerPlayer player, ResourceLocation questId) {
        return boundedCompletion(() -> synchronizedOwner(player, () -> {
            var quest = QuestBookManager.get().active().map(s -> s.quests().get(questId)).orElse(null);
            if (quest == null) return OperationResult.failure("NOT_FOUND", "Unknown quest");
            QuestProgressData data = QuestProgressData.get(player.getServer());
            PlayerProgress progress = data.get(ProgressOwnerService.require(player));
            QuestStatus status = progress.status(questId.toString());
            if (status == QuestStatus.COMPLETED || status == QuestStatus.REWARD_CLAIMED) return OperationResult.noChange("ALREADY_COMPLETED", "Quest already completed");
            // Administrative/API completion intentionally bypasses task resources;
            // callers have already made an explicit server-side authority decision.
            quest.tasks().forEach(task -> {
                if (progress.taskProgress(task.id().toString()) < 1) {
                    changeTaskProgress(player, quest, task, progress, Long.MAX_VALUE / 4);
                }
            });
            return markCompleted(player, quest, progress, data);
        }));
    }

    public OperationResult addTaskProgress(ServerPlayer player, ResourceLocation taskId, long amount) {
        if (amount <= 0) return OperationResult.failure("INVALID_AMOUNT", "Amount must be positive");
        QuestDefinition owner = QuestBookManager.get().active().stream().flatMap(snapshot -> snapshot.book().quests().stream())
                .filter(quest -> quest.tasks().stream().anyMatch(task -> task.id().equals(taskId))).findFirst().orElse(null);
        if (owner == null) return OperationResult.failure("NOT_FOUND", "Unknown task " + taskId);
        TaskDefinition task = owner.tasks().stream().filter(candidate -> candidate.id().equals(taskId)).findFirst().orElseThrow();
        PlayerProgress progress = progress(player);
        changeTaskProgress(player, owner, task, progress, amount);
        QuestProgressData.get(player.getServer()).setDirty();
        BrnQuestNetwork.syncProgress(player, true);
        return OperationResult.success("Task progress updated");
    }

    public OperationResult claim(ServerPlayer player, ResourceLocation rewardId) {
        return synchronizedOwner(player, () -> {
            var snapshot = QuestBookManager.get().active().orElse(null);
            if (snapshot == null) return OperationResult.failure("NO_BOOK", "No active book");
            QuestDefinition owner = snapshot.book().quests().stream().filter(q -> q.rewards().stream().anyMatch(r -> r.id().equals(rewardId))).findFirst().orElse(null);
            if (owner == null) return OperationResult.failure("NOT_FOUND", "Unknown reward");
            PlayerProgress progress = progress(player);
            if (progress.status(owner.id().toString()).ordinal() < QuestStatus.COMPLETED.ordinal()) return OperationResult.failure("LOCKED", "Quest is incomplete");
            if (progress.isClaimed(rewardId.toString())) return OperationResult.noChange("ALREADY_CLAIMED", "Reward already claimed");
            RewardDefinition reward = owner.rewards().stream().filter(r -> r.id().equals(rewardId)).findFirst().orElseThrow();
            RewardType<?> type = RewardTypeRegistry.get(reward.typeId());
            if (type == null) return OperationResult.failure("UNKNOWN_TYPE", "Unknown reward type");
            // The ledger is persisted before the non-repeatable side effect to prevent crash duplication.
            progress.claim(rewardId.toString());
            QuestProgressData data = QuestProgressData.get(player.getServer());
            data.setDirty();
            var result = RewardTypeExecutor.execute(type, new RewardContext(player, owner.bookId(), owner.id(),
                    ApiViews.reward(reward)));
            if (!result.success()) return OperationResult.failure("EXECUTION_FAILED", result.message());
            if (owner.rewards().stream().allMatch(r -> progress.isClaimed(r.id().toString()))) progress.status(owner.id().toString(), QuestStatus.REWARD_CLAIMED);
            BrnQuestEvents.post(new RewardClaimedEvent(player.getUUID(), player.getScoreboardName(),
                    snapshot.book().id(), owner.id(), rewardId, ApiViews.reward(reward),
                    BrnQuestApi.getProgress(player, owner.id().toString()).orElseThrow()));
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
        if (status == QuestStatus.AVAILABLE) {
            // The HUD represents one focused objective, so activating a new quest replaces the old focus.
            progress.questsView().entrySet().stream().filter(entry -> entry.getValue() == QuestStatus.ACTIVE)
                    .map(Map.Entry::getKey).forEach(id -> progress.status(id, QuestStatus.AVAILABLE));
        }
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
        BrnQuestEvents.post(new QuestCompletedEvent(player.getUUID(), player.getScoreboardName(), quest.bookId(),
                quest.id(), ApiViews.quest(quest), BrnQuestApi.getProgress(player, quest.id().toString()).orElseThrow()));
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

    private long changeTaskProgress(ServerPlayer player, QuestDefinition quest, TaskDefinition task,
                                    PlayerProgress progress, long amount) {
        long previous = progress.taskProgress(task.id().toString());
        long current = progress.addTaskProgress(task.id().toString(), amount);
        if (previous != current) {
            // Mark the authoritative world data dirty before observers see the committed value.
            QuestProgressData.get(player.getServer()).setDirty();
            BrnQuestEvents.post(new TaskProgressChangedEvent(player.getUUID(), player.getScoreboardName(),
                    quest.bookId(), quest.id(), task.id(), ApiViews.task(task), previous, current));
        }
        return current;
    }

    private TaskContext taskContext(ServerPlayer player, QuestDefinition quest, TaskDefinition task,
                                    PlayerProgress progress) {
        return new TaskContext(player, quest.bookId(), quest.id(), ApiViews.task(task),
                progress.taskProgress(task.id().toString()));
    }

    private OperationResult boundedCompletion(java.util.function.Supplier<OperationResult> operation) {
        int depth = COMPLETION_DEPTH.get();
        if (depth >= MAX_CHAIN_OPERATIONS) return OperationResult.failure("CHAIN_LIMIT", "Completion chain exceeded " + MAX_CHAIN_OPERATIONS + " operations");
        COMPLETION_DEPTH.set(depth + 1);
        try { return operation.get(); }
        finally { COMPLETION_DEPTH.set(depth); }
    }

    private <T> T synchronizedOwner(ServerPlayer player, java.util.function.Supplier<T> operation) {
        ProgressOwnerId owner = ProgressOwnerService.require(player);
        Object lock;
        // Shared providers will serialize all members through the same stable owner key.
        synchronized (locks) { lock = locks.computeIfAbsent(owner, ignored -> new Object()); }
        synchronized (lock) { return operation.get(); }
    }
}
