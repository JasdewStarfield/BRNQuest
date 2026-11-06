package yourscraft.jasdewstarfield.brnquest.progress;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.api.ApiViews;
import yourscraft.jasdewstarfield.brnquest.api.OperationResult;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.data.RewardDefinition;
import yourscraft.jasdewstarfield.brnquest.data.TaskDefinition;
import yourscraft.jasdewstarfield.brnquest.event.TaskProgressChangedEvent;
import yourscraft.jasdewstarfield.brnquest.network.BrnQuestNetwork;
import yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerId;
import yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerService;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;
import yourscraft.jasdewstarfield.brnquest.task.TaskType;
import yourscraft.jasdewstarfield.brnquest.task.TaskContext;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypeExecutor;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypeRegistry;
import yourscraft.jasdewstarfield.brnquest.task.TaskSubmissionSelection;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/** Server-thread transaction coordinator for owner-scoped progress and idempotent reward claims. */
public final class ProgressEngine {
    private static final ProgressEngine INSTANCE = new ProgressEngine();
    private static final int MAX_CHAIN_OPERATIONS = 256;
    private static final ThreadLocal<Integer> COMPLETION_DEPTH = ThreadLocal.withInitial(() -> 0);
    private final Map<ProgressOwnerId, Object> locks = new WeakHashMap<>();
    private final ProgressQueries queries = new ProgressQueries();

    private final ProgressEffects effects;
    private final ProgressSampling sampling;
    private final ProgressRewards rewards;
    private final ProgressCompletion completion;

    private ProgressEngine() { this(ProgressEffects.LIVE); }

    /** Package-local fixture construction observes effects without changing the public singleton entry point. */
    ProgressEngine(ProgressEffects effects) {
        this.effects = java.util.Objects.requireNonNull(effects);
        sampling = new ProgressSampling(queries, effects, new ProgressSampling.Host() {
            public OperationResult complete(ServerPlayer player, ResourceLocation questId) {
                return ProgressEngine.this.complete(player, questId, false);
            }
            public long changeTaskProgress(ServerPlayer player, QuestDefinition quest, TaskDefinition task, PlayerProgress progress, long amount) {
                return ProgressEngine.this.changeTaskProgress(player, quest, task, progress, amount);
            }
        });
        rewards = new ProgressRewards(queries, effects, new ProgressRewards.Host() {
            public OperationResult claim(ServerPlayer player, ResourceLocation rewardId, boolean notifyAutomatic) {
                return ProgressEngine.this.claim(player, rewardId, notifyAutomatic);
            }
            public void claimCommitted(ServerPlayer player, QuestDefinition quest, PlayerProgress progress) {
                if (completion.advanceRepeatIfReady(quest, progress, System.currentTimeMillis())) sampling.prepareOwnerSamples(player);
            }
        });
        completion = new ProgressCompletion(queries, effects, new ProgressCompletion.Host() {
            public long changeTaskProgress(ServerPlayer player, QuestDefinition quest, TaskDefinition task, PlayerProgress progress, long amount) {
                return ProgressEngine.this.changeTaskProgress(player, quest, task, progress, amount);
            }
            public String trackedQuest(ServerPlayer player) { return ProgressEngine.this.trackedQuest(player); }
            public void prepareOwnerSamples(ServerPlayer player) { sampling.prepareOwnerSamples(player); }
            public void prepareOwnerSamplesNow(ServerPlayer player) { sampling.prepareOwnerSamplesNow(player); }
            public void resetTransientStates(ServerPlayer player, QuestDefinition quest, List<TaskDefinition> tasks) { sampling.resetTransientStates(player, quest, tasks); }
            public void restoreMainInventory(ServerPlayer player, List<ItemStack> inventory) { ProgressEngine.this.restoreMainInventory(player, inventory); }
            public void reconcile(ServerPlayer player) { ProgressEngine.this.reconcile(player); }
            public void deliverCompleted(ServerPlayer player, QuestDefinition quest) { rewards.deliverCompleted(player, quest); }
            public OperationResult forceComplete(ServerPlayer player, ResourceLocation questId) { return ProgressEngine.this.forceComplete(player, questId); }
            public void reset(ServerPlayer player, ResourceLocation questId) { ProgressEngine.this.reset(player, questId); }
            public OperationResult completeWithinBudget(java.util.function.Supplier<OperationResult> operation) { return boundedCompletion(operation); }
        });
    }
    public static ProgressEngine get() { return INSTANCE; }

    public PlayerProgress progress(ServerPlayer player) {
        return queries.progress(player);
    }

    public long taskProgress(ServerPlayer player, QuestDefinition quest, TaskDefinition task) {
        return queries.taskProgress(player, quest, task);
    }
    /** Every recipient receives their own objective counters, while quest status stays team-wide. */
    public Map<String, Long> visibleTaskProgress(ServerPlayer player) {
        return queries.visibleTaskProgress(player);
    }

    /** Waiting is server-authored so inventory previews cannot claim personal completion. */
    public Set<String> waitingForTeam(ServerPlayer player) {
        return queries.waitingForTeam(player);
    }
    public boolean rewardClaimed(ServerPlayer player, RewardDefinition reward) {
        return queries.rewardClaimed(player, reward);
    }
    public Set<String> visibleClaims(ServerPlayer player) {
        return queries.visibleClaims(player);
    }
    public Map<String, QuestStatus> visibleStatuses(ServerPlayer player) {
        return queries.visibleStatuses(player);
    }
    /** Sampling proposals commit under the same owner lock as submission and completion. */
    public void pollTasks(ServerPlayer player) {
        synchronizedOwner(player, () -> { sampling.pollTasks(player); return null; });
    }

    /** Sampling proposals commit under the same owner lock as submission and completion. */
    public void submitInventoryTasks(ServerPlayer player) {
        synchronizedOwner(player, () -> { sampling.submitInventoryTasks(player); return null; });
    }

    /** Sampling proposals commit under the same owner lock as submission and completion. */
    public void recordCraft(ServerPlayer player, ItemStack crafted) {
        if (crafted.isEmpty()) return;
        synchronizedOwner(player, () -> { sampling.recordCraft(player, crafted); return null; });
    }

    public void reconcile(ServerPlayer player) {
        completion.reconcile(player);
    }

    /** Performs time-based repeat reopening even when no inventory event occurs. */
    public void tick(ServerPlayer player) {
        pollTasks(player);
        // Server time is the stable cadence source; player tick counters can reset during lifecycle transitions.
        if (player.getServer().getTickCount() % 20 != 0) return;
        var snapshot = QuestBookManager.get().active().orElse(null);
        if (snapshot == null) return;
        if (queries.shared(player)) {
            rewards.deliverAutomatic(player);
            var ledger = progress(player);
            var members = ProgressOwnerService.resolve(player).orElseThrow().members();
            for (var quest : snapshot.book().quests()) {
                if (!quest.behavior().requireAllTeamMembers() || quest.tasks().isEmpty()) continue;
                var status = ledger.status(quest.id().toString());
                if ((status == QuestStatus.AVAILABLE || status == QuestStatus.ACTIVE)
                        && ledger.allMembersCompleted(quest.id().toString(), members)
                        && queries.dependenciesComplete(quest, ledger)) {
                    boundedCompletion(() -> synchronizedOwner(player, () -> completion.markCompleted(player, quest, ledger,
                            QuestProgressData.get(player.getServer()))));
                }
            }
        }
        PlayerProgress progress = progress(player);
        boolean changed = false;
        long now = System.currentTimeMillis();
        for (QuestDefinition quest : sampling.quests(snapshot)) {
            changed |= completion.advanceRepeatIfReady(quest, progress, now);
        }
        if (changed) {
            QuestProgressData.get(player.getServer()).setDirty();
            sampling.prepareOwnerSamples(player);
            effects.sync(player, true);
        }
    }

    /** Server-authored visibility set; clients render it but never infer hidden quest access. */
    public Set<String> visibleQuestIds(ServerPlayer player) {
        return queries.visibleQuestIds(player);
    }

    /** Applies unambiguous ledger aliases while preserving any source ID that is live again. */
    static void reconcileLegacyIds(QuestBookDefinition book, PlayerProgress progress) {
        ProgressCompletion.reconcileLegacyIds(book, progress);
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
        return boundedCompletion(() -> synchronizedOwner(player, () -> completion.completeLocked(player, questId, checkmarkIntent)));
    }

    /** Applies one task-row intent, then lets the normal transaction decide whether the quest can finish. */
    public OperationResult completeTask(ServerPlayer player, ResourceLocation questId, ResourceLocation taskId) {
        return completeTask(player, questId, taskId, TaskSubmissionSelection.AUTOMATIC);
    }

    /** Applies a bounded child-entry selection that the task type revalidates inside the owner lock. */
    public OperationResult completeTask(ServerPlayer player, ResourceLocation questId, ResourceLocation taskId,
                                        TaskSubmissionSelection selection) {
        return submitTask(player, questId, taskId, selection, false);
    }

    /** Reports the committed row independently from whether the enclosing quest can finish yet. */
    public OperationResult submitTask(ServerPlayer player, ResourceLocation questId, ResourceLocation taskId,
                                      TaskSubmissionSelection selection) {
        return submitTask(player, questId, taskId, selection, true);
    }

    private OperationResult submitTask(ServerPlayer player, ResourceLocation questId, ResourceLocation taskId,
                                       TaskSubmissionSelection selection, boolean reportSubmission) {
        OperationResult result = synchronizedOwner(player, () -> {
            var snapshot = QuestBookManager.get().active().orElse(null);
            QuestDefinition quest = snapshot == null ? null : snapshot.quests().get(questId);
            if (quest == null) return OperationResult.failure("NOT_FOUND", "Unknown quest " + questId);
            TaskDefinition task = quest.tasks().stream().filter(candidate -> candidate.id().equals(taskId)).findFirst().orElse(null);
            if (task == null) return OperationResult.failure("NOT_FOUND", "Task does not belong to quest " + taskId);
            PlayerProgress progress = progress(player);
            // Submission is an idempotent command. Check the per-task ledger before quest
            // status so a retransmission after quest completion is also a successful no-op.
            if (queries.taskLedger(player, quest, progress).taskProgress(taskId.toString()) >= 1) {
                return OperationResult.noChange("ALREADY_SUBMITTED", "Task already submitted");
            }
            QuestStatus status = progress.status(questId.toString());
            if (status != QuestStatus.AVAILABLE && status != QuestStatus.ACTIVE) {
                return OperationResult.failure("LOCKED", "Quest is not available");
            }
            if (!queries.dependenciesComplete(quest, progress)) return OperationResult.failure("LOCKED", "Quest dependencies are incomplete");
            if (!queries.taskIsCurrent(player, quest, task, progress)) {
                return OperationResult.failure("OUT_OF_SEQUENCE", "An earlier required objective must be completed first");
            }

            TaskType<?> type = TaskTypeRegistry.get(task.typeId());
            if (type == null) return OperationResult.failure("UNKNOWN_TYPE", "Unknown task type " + task.typeId());
            TaskContext context = queries.taskContext(player, quest, task, progress);
            if (!TaskTypeExecutor.allowsManualSubmission(type, context.task())) {
                return OperationResult.failure("NOT_SUBMITTABLE", "Task does not accept manual submission: " + task.id());
            }
            List<net.minecraft.world.item.ItemStack> inventoryBeforeSubmit = player.getInventory().items.stream()
                    .map(net.minecraft.world.item.ItemStack::copy).toList();
            yourscraft.jasdewstarfield.brnquest.task.TaskSubmissionResult submission;
            try {
                submission = TaskTypeExecutor.submit(type, context, selection);
            } catch (RuntimeException failure) {
                // A faulty type cannot leave a partially consumed main inventory without a submission receipt.
                restoreMainInventory(player, inventoryBeforeSubmit);
                return OperationResult.failure("TASK_SUBMISSION_FAILED", "Task submission failed: " + task.id());
            }
            if (!submission.success()) {
                restoreMainInventory(player, inventoryBeforeSubmit);
                return OperationResult.failure(submission.code(), submission.message() + ": " + task.id());
            }
            changeTaskProgress(player, quest, task, progress, 1);
            QuestProgressData.get(player.getServer()).setDirty();

            var completion = complete(player, questId, false);
            // Legacy callers keep their whole-quest result. New callers get the fact already committed above.
            return reportSubmission ? new OperationResult(yourscraft.jasdewstarfield.brnquest.api.OperationStatus.SUCCESS,
                    "TASK_SUBMITTED", "Task submitted") : completion;
        });
        // A response is sent for both accepted and rejected intents so the client can clear
        // its pending-click guard. Full inventory state avoids stale counts on paused screens.
        effects.sync(player, true);
        player.inventoryMenu.broadcastFullState();
        return result;
    }

    public OperationResult forceComplete(ServerPlayer player, ResourceLocation questId) {
        return boundedCompletion(() -> synchronizedOwner(player, () -> completion.forceCompleteLocked(player, questId)));
    }

    public OperationResult addTaskProgress(ServerPlayer player, ResourceLocation taskId, long amount) {
        if (amount <= 0) return OperationResult.failure("INVALID_AMOUNT", "Amount must be positive");
        QuestDefinition owner = QuestBookManager.get().active().stream().flatMap(snapshot -> snapshot.book().quests().stream())
                .filter(quest -> quest.tasks().stream().anyMatch(task -> task.id().equals(taskId))).findFirst().orElse(null);
        if (owner == null) return OperationResult.failure("NOT_FOUND", "Unknown task " + taskId);
        TaskDefinition task = owner.tasks().stream().filter(candidate -> candidate.id().equals(taskId)).findFirst().orElseThrow();
        PlayerProgress progress = progress(player);
        if (!queries.dependenciesComplete(owner, progress) || !queries.taskIsCurrent(player, owner, task, progress)) {
            return OperationResult.failure("LOCKED", "Task is not currently progressable");
        }
        changeTaskProgress(player, owner, task, progress, amount);
        QuestProgressData.get(player.getServer()).setDirty();
        effects.sync(player, true);
        return OperationResult.success("Task progress updated");
    }

    public OperationResult claim(ServerPlayer player, ResourceLocation rewardId) {
        return claim(player, rewardId, false);
    }

    private OperationResult claim(ServerPlayer player, ResourceLocation rewardId, boolean notifyAutomatic) {
        return synchronizedOwner(player, () -> rewards.claimLocked(player, rewardId, notifyAutomatic));
    }

    public void reset(ServerPlayer player, ResourceLocation questId) {
        synchronizedOwner(player, () -> { completion.resetLocked(player, questId); return null; });
    }

    /** Called only by the permission/revision-checked admin service while holding the same owner lock. */
    OperationResult administer(ServerPlayer player, QuestDefinition quest, ResourceLocation taskId,
                               AdminProgressAction action) {
        return completion.administerLocked(player, quest, taskId, action);
    }

    /** The durable personal tracking record survives terminal lifecycle states and remains independent of team progress. */
    public String trackedQuest(ServerPlayer player) {
        QuestProgressData data = QuestProgressData.get(player.getServer());
        String tracked = data.tracked(player.getUUID());
        if (!tracked.isEmpty()) return tracked;
        if (!queries.shared(player)) {
            // Migrate the older personal ACTIVE representation once, before completion replaces that lifecycle state.
            tracked = progress(player).questsView().entrySet().stream()
                    .filter(entry -> entry.getValue() == QuestStatus.ACTIVE).map(Map.Entry::getKey).findFirst().orElse("");
            if (!tracked.isEmpty()) data.tracked(player.getUUID(), tracked);
        }
        return tracked;
    }

    public OperationResult toggleTracked(ServerPlayer player, ResourceLocation questId) {
        PlayerProgress progress = progress(player);
        QuestStatus status = progress.status(questId.toString());
        if (!status.trackable() || !visibleQuestIds(player).contains(questId.toString()))
            return OperationResult.failure("NOT_TRACKABLE", "Quest is not visible or trackable");
        String next = questId.toString().equals(trackedQuest(player)) ? "" : questId.toString();
        boolean shared = queries.shared(player);
        if (!shared) {
            // Preserve legacy ACTIVE display semantics without ever overwriting completed or reward-claimed states.
            progress.questsView().entrySet().stream().filter(entry -> entry.getValue() == QuestStatus.ACTIVE)
                    .map(Map.Entry::getKey).toList().forEach(id -> progress.status(id, QuestStatus.AVAILABLE));
            if (!next.isEmpty() && progress.status(next) == QuestStatus.AVAILABLE) progress.status(next, QuestStatus.ACTIVE);
        }
        QuestProgressData.get(player.getServer()).tracked(player.getUUID(), next);
        effects.sync(player, !shared);
        return OperationResult.success("Tracking updated");
    }

    /** Preserves terminal history while allowing prerequisite edits and resets to demote stale availability. */
    static QuestStatus reconciledAvailability(QuestStatus current, boolean dependenciesSatisfied) {
        return ProgressQueries.reconciledAvailability(current, dependenciesSatisfied);
    }

    private void restoreMainInventory(ServerPlayer player, List<net.minecraft.world.item.ItemStack> snapshot) {
        for (int index = 0; index < snapshot.size(); index++) player.getInventory().items.set(index, snapshot.get(index));
        player.getInventory().setChanged();
    }

    private long changeTaskProgress(ServerPlayer player, QuestDefinition quest, TaskDefinition task,
                                    PlayerProgress progress, long amount) {
        long previous = queries.taskLedger(player, quest, progress).taskProgress(task.id().toString());
        long current = queries.taskLedger(player, quest, progress).addTaskProgress(task.id().toString(), amount);
        if (previous != current) {
            // Mark the authoritative world data dirty before observers see the committed value.
            QuestProgressData.get(player.getServer()).setDirty();
            sampling.progressChanged(player, quest, progress);
            effects.publish(new TaskProgressChangedEvent(player.getUUID(), player.getScoreboardName(),
                    quest.bookId(), quest.id(), task.id(), ApiViews.task(task), previous, current));
        }
        return current;
    }

    private OperationResult boundedCompletion(java.util.function.Supplier<OperationResult> operation) {
        int depth = COMPLETION_DEPTH.get();
        if (depth >= MAX_CHAIN_OPERATIONS) return OperationResult.failure("CHAIN_LIMIT", "Completion chain exceeded " + MAX_CHAIN_OPERATIONS + " operations");
        COMPLETION_DEPTH.set(depth + 1);
        try { return operation.get(); }
        finally { COMPLETION_DEPTH.set(depth); }
    }

    <T> T synchronizedOwner(ServerPlayer player, java.util.function.Supplier<T> operation) {
        var view = ProgressOwnerService.resolve(player).orElseThrow();
        ProgressOwnerId owner = view.id();
        QuestProgressData.get(player.getServer()).observe(owner, view.members(), view.lifecycle());
        Object lock;
        // Shared providers will serialize all members through the same stable owner key.
        synchronized (locks) { lock = locks.computeIfAbsent(owner, ignored -> new Object()); }
        synchronized (lock) { return operation.get(); }
    }
}
