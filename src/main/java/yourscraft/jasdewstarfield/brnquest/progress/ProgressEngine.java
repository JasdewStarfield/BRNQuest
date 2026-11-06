package yourscraft.jasdewstarfield.brnquest.progress;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.api.ApiViews;
import yourscraft.jasdewstarfield.brnquest.api.BrnQuestApi;
import yourscraft.jasdewstarfield.brnquest.api.OperationResult;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.data.RewardDefinition;
import yourscraft.jasdewstarfield.brnquest.data.TaskDefinition;
import yourscraft.jasdewstarfield.brnquest.event.QuestCompletedEvent;
import yourscraft.jasdewstarfield.brnquest.event.RewardClaimedEvent;
import yourscraft.jasdewstarfield.brnquest.event.TaskProgressChangedEvent;
import yourscraft.jasdewstarfield.brnquest.network.BrnQuestNetwork;
import yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerId;
import yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerService;
import yourscraft.jasdewstarfield.brnquest.reward.RewardClaimContext;
import yourscraft.jasdewstarfield.brnquest.reward.RewardClaimResult;
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
    }
    public static ProgressEngine get() { return INSTANCE; }

    public PlayerProgress progress(ServerPlayer player) {
        return queries.progress(player);
    }

    /** Objectives for all-member quests are personal within the active team and completion cycle. */
    private boolean allMembers(ServerPlayer player, QuestDefinition quest) {
        return queries.allMembers(player, quest);
    }
    private PlayerProgress taskLedger(ServerPlayer player, QuestDefinition quest, PlayerProgress progress) {
        return queries.taskLedger(player, quest, progress);
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
    private boolean shared(ServerPlayer player) {
        return queries.shared(player);
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
    /** Repeat blocking uses the frozen cohort, including offline members, rather than the first claimant. */
    private boolean rewardsResolved(QuestDefinition quest, PlayerProgress progress) {
        return queries.rewardsResolved(quest, progress);
    }
    /** Automatic delivery is retried idempotently for eligible online members; offline members wait for login. */
    private void deliverAutomatic(ServerPlayer player) {
        var snapshot = QuestBookManager.get().active().orElse(null);
        if (snapshot == null) return;
        for (QuestDefinition quest : snapshot.book().quests()) {
            QuestStatus status = progress(player).status(quest.id().toString());
            if (status == QuestStatus.COMPLETED || status == QuestStatus.REWARD_CLAIMED) {
                for (RewardDefinition reward : quest.rewards()) if (automaticAllowed(reward)
                        && !rewardClaimed(player, reward)) claim(player, reward.id(), reward.policy().notifyPlayer());
            }
        }
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
            advanceRepeatIfReady(quest, progress, System.currentTimeMillis());
            QuestStatus existing = progress.status(quest.id().toString());
            QuestStatus reconciled = reconciledAvailability(existing, dependenciesComplete(quest, progress));
            if (reconciled != existing) progress.status(quest.id().toString(), reconciled);
        }
        progress.questsView().keySet().stream().filter(id -> !current.contains(id)).forEach(progress::orphan);
        progress.revision(snapshot.revision());
        sampling.prepareOwnerSamples(player);
        data.setDirty();
    }

    /** Performs time-based repeat reopening even when no inventory event occurs. */
    public void tick(ServerPlayer player) {
        pollTasks(player);
        // Server time is the stable cadence source; player tick counters can reset during lifecycle transitions.
        if (player.getServer().getTickCount() % 20 != 0) return;
        var snapshot = QuestBookManager.get().active().orElse(null);
        if (snapshot == null) return;
        if (shared(player)) {
            deliverAutomatic(player);
            var ledger = progress(player);
            var members = ProgressOwnerService.resolve(player).orElseThrow().members();
            for (var quest : snapshot.book().quests()) {
                if (!quest.behavior().requireAllTeamMembers() || quest.tasks().isEmpty()) continue;
                var status = ledger.status(quest.id().toString());
                if ((status == QuestStatus.AVAILABLE || status == QuestStatus.ACTIVE)
                        && ledger.allMembersCompleted(quest.id().toString(), members)
                        && dependenciesComplete(quest, ledger)) {
                    boundedCompletion(() -> synchronizedOwner(player, () -> markCompleted(player, quest, ledger,
                            QuestProgressData.get(player.getServer()))));
                }
            }
        }
        PlayerProgress progress = progress(player);
        boolean changed = false;
        long now = System.currentTimeMillis();
        for (QuestDefinition quest : sampling.quests(snapshot)) {
            changed |= advanceRepeatIfReady(quest, progress, now);
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
            // An empty authoring placeholder has no completion condition; keep its progress untouched until configured.
            if (quest.tasks().isEmpty()) return OperationResult.failure("NO_OBJECTIVES", "Quest has no objectives configured");
            if (allMembers(player, quest)
                    && progress.objectives(player.getUUID()).status(questId.toString()) == QuestStatus.COMPLETED) {
                return finishMemberCompletion(player, quest, progress, data);
            }
            if (checkmarkIntent) {
                // Quest-wide completion remains a generic intent; each task type decides whether it accepts it.
                for (TaskDefinition task : quest.tasks()) {
                    TaskType<?> type = TaskTypeRegistry.get(task.typeId());
                    TaskContext context = taskContext(player, quest, task, progress);
                    if (type != null && taskIsCurrent(player, quest, task, progress)
                            && TaskTypeExecutor.acceptsQuestCompletionIntent(type, context.task())
                            && TaskTypeExecutor.submit(type, context).success()) {
                        changeTaskProgress(player, quest, task, progress, 1);
                        // Sequential mode represents one author-ordered step per user intent.
                        if (quest.behavior().sequentialTasks()) break;
                    }
                }
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
                // Submitted rows already consumed once; unsubmitted optional rows must not be
                // swept into another row's completion transaction or spend the player's items.
                if (type != null && !task.optional() && taskLedger(player, quest, progress).taskProgress(task.id().toString()) < 1
                        && !TaskTypeExecutor.consume(type, taskContext(player, quest, task, progress))) {
                    restoreMainInventory(player, inventoryBeforeConsume);
                    return OperationResult.failure("CONSUME_FAILED", "Could not consume task items");
                }
            }
            if (allMembers(player, quest)) {
                // Inventory-satisfied objectives need a receipt before resources disappear or the player logs out.
                for (var task : quest.tasks()) if (!task.optional()
                        && taskLedger(player, quest, progress).taskProgress(task.id().toString()) == 0) {
                    changeTaskProgress(player, quest, task, progress, 1);
                }
                data.setDirty();
                return finishMemberCompletion(player, quest, progress, data);
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
            if (taskLedger(player, quest, progress).taskProgress(taskId.toString()) >= 1) {
                return OperationResult.noChange("ALREADY_SUBMITTED", "Task already submitted");
            }
            QuestStatus status = progress.status(questId.toString());
            if (status != QuestStatus.AVAILABLE && status != QuestStatus.ACTIVE) {
                return OperationResult.failure("LOCKED", "Quest is not available");
            }
            if (!dependenciesComplete(quest, progress)) return OperationResult.failure("LOCKED", "Quest dependencies are incomplete");
            if (!taskIsCurrent(player, quest, task, progress)) {
                return OperationResult.failure("OUT_OF_SEQUENCE", "An earlier required objective must be completed first");
            }

            TaskType<?> type = TaskTypeRegistry.get(task.typeId());
            if (type == null) return OperationResult.failure("UNKNOWN_TYPE", "Unknown task type " + task.typeId());
            TaskContext context = taskContext(player, quest, task, progress);
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
                if (taskLedger(player, quest, progress).taskProgress(task.id().toString()) < 1) {
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
        if (!dependenciesComplete(owner, progress) || !taskIsCurrent(player, owner, task, progress)) {
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
        return synchronizedOwner(player, () -> {
            var snapshot = QuestBookManager.get().active().orElse(null);
            if (snapshot == null) return OperationResult.failure("NO_BOOK", "No active book");
            QuestDefinition owner = snapshot.book().quests().stream().filter(q -> q.rewards().stream().anyMatch(r -> r.id().equals(rewardId))).findFirst().orElse(null);
            if (owner == null) return OperationResult.failure("NOT_FOUND", "Unknown reward");
            PlayerProgress progress = progress(player);
            if (progress.status(owner.id().toString()).ordinal() < QuestStatus.COMPLETED.ordinal()) return OperationResult.failure("LOCKED", "Quest is incomplete");
            RewardDefinition reward = owner.rewards().stream().filter(r -> r.id().equals(rewardId)).findFirst().orElseThrow();
            if (rewardClaimed(player, reward))
                return OperationResult.noChange("ALREADY_CLAIMED", "Reward already claimed");
            if (shared(player) && !progress.completionMembers(owner.id().toString()).contains(player.getUUID())) {
                return OperationResult.failure("NOT_ELIGIBLE", "Player was not a member when this cycle completed");
            }
            RewardType<?> type = RewardTypeRegistry.get(reward.typeId());
            if (type == null) return OperationResult.failure("UNKNOWN_TYPE", "Unknown reward type");
            var execution = new RewardContext(player, owner.bookId(), owner.id(), ApiViews.reward(reward));
            var handler = type.claimHandler();
            QuestProgressData data = QuestProgressData.get(player.getServer());
            // Full reset can distinguish an unresolved attempt from an already empty quest.
            progress.rewardAttempted(owner.id().toString());
            data.setDirty();
            String message;
            if (handler.isPresent()) {
                // Advanced types own attempt persistence; only a confirmed success commits the core ledger.
                RewardClaimResult result;
                try {
                    result = java.util.Objects.requireNonNull(handler.orElseThrow().claim(new RewardClaimContext(
                            execution, ProgressOwnerService.require(player), progress.completionCycles(owner.id().toString()), progress.claimGeneration(owner.id().toString()))));
                } catch (Exception error) {
                    yourscraft.jasdewstarfield.brnquest.BRNQuest.LOGGER.error("Reward claim handler failed for {} ({})", rewardId, reward.typeId(), error);
                    return OperationResult.failure("CLAIM_HANDLER_FAILED", "Reward attempt failed; consult the type's recovery policy");
                }
                if (result.state() != RewardClaimResult.State.SUCCESS) {
                    effects.sync(player, true);
                    return result.state() == RewardClaimResult.State.PENDING
                            ? OperationResult.noChange(result.code(), result.message())
                            : OperationResult.failure(result.code(), result.message());
                }
                message = result.message();
                if (shared(player) && !reward.teamReward()) progress.claimMember(player.getUUID(), rewardId.toString());
                else progress.claim(rewardId.toString());
                data.setDirty();
            } else {
                // Preserve the existing ordinary-reward policy for extensions that only implement execute.
                if (shared(player) && !reward.teamReward()) progress.claimMember(player.getUUID(), rewardId.toString());
                else progress.claim(rewardId.toString());
                data.setDirty();
                var result = RewardTypeExecutor.execute(type, execution);
                if (!result.success()) return OperationResult.failure("EXECUTION_FAILED", result.message());
                message = result.message();
            }
            if (rewardsResolved(owner, progress)) progress.status(owner.id().toString(), QuestStatus.REWARD_CLAIMED);
            effects.publish(new RewardClaimedEvent(player.getUUID(), player.getScoreboardName(),
                    snapshot.book().id(), owner.id(), rewardId, ApiViews.reward(reward),
                    BrnQuestApi.getProgress(player, owner.id().toString()).orElseThrow()));
            if (advanceRepeatIfReady(owner, progress, System.currentTimeMillis())) sampling.prepareOwnerSamples(player);
            effects.sync(player, true);
            if (notifyAutomatic) {
                player.displayClientMessage(Component.translatable("message.brnquest.reward.auto_claimed",
                        rewardId.toString()), true);
            }
            return OperationResult.success(message);
        });
    }

    public void reset(ServerPlayer player, ResourceLocation questId) {
        synchronizedOwner(player, () -> {
            var quest = QuestBookManager.get().active().map(s -> s.quests().get(questId)).orElse(null);
            if (quest == null) return null;
            progress(player).resetQuest(questId.toString(), quest.tasks().stream().map(t -> t.id().toString()).toList(),
                    quest.rewards().stream().map(r -> r.id().toString()).toList());
            sampling.resetTransientStates(player, quest, quest.tasks());
            QuestProgressData.get(player.getServer()).setDirty();
            reconcile(player);
            effects.sync(player, false);
            return null;
        });
    }

    /** Called only by the permission/revision-checked admin service while holding the same owner lock. */
    OperationResult administer(ServerPlayer player, QuestDefinition quest, ResourceLocation taskId,
                               AdminProgressAction action) {
        PlayerProgress progress = progress(player);
        if (action == AdminProgressAction.FORCE_QUEST) return forceComplete(player, quest.id());
        if (action == AdminProgressAction.RESET_QUEST) {
            reset(player, quest.id());
            return OperationResult.success("Quest progress reset");
        }
        TaskDefinition task = quest.tasks().stream().filter(value -> value.id().equals(taskId)).findFirst().orElseThrow();
        if (action == AdminProgressAction.RESET_TASK) {
            long previous = taskLedger(player, quest, progress).taskProgress(taskId.toString());
            progress.resetTask(quest.id().toString(), taskId.toString(),
                    dependenciesComplete(quest, progress) ? QuestStatus.AVAILABLE : QuestStatus.LOCKED);
            QuestProgressData.get(player.getServer()).setDirty();
            sampling.resetTransientStates(player, quest, List.of(task));
            if (previous != 0) effects.publish(new TaskProgressChangedEvent(player.getUUID(),
                    player.getScoreboardName(), quest.bookId(), quest.id(), taskId, ApiViews.task(task), previous, 0));
            // A task reset can invalidate descendants, so recompute the owner graph before synchronization.
            reconcile(player);
            return OperationResult.success("Objective progress reset; reward claims preserved");
        }
        if (taskLedger(player, quest, progress).taskProgress(taskId.toString()) >= 1) {
            return OperationResult.noChange("ALREADY_SUBMITTED", "Objective already completed");
        }
        // Do not call the normal submission path: it may consume other currently satisfied items.
        changeTaskProgress(player, quest, task, progress, Long.MAX_VALUE / 4);
        QuestStatus status = progress.status(quest.id().toString());
        if (status != QuestStatus.COMPLETED && status != QuestStatus.REWARD_CLAIMED
                && dependenciesComplete(quest, progress)
                && quest.tasks().stream().filter(value -> !value.optional())
                .allMatch(value -> taskLedger(player, quest, progress).taskProgress(value.id().toString()) >= 1)) {
            if (allMembers(player, quest)) {
                return boundedCompletion(() -> finishMemberCompletion(player, quest, progress, QuestProgressData.get(player.getServer())));
            }
            return boundedCompletion(() -> markCompleted(player, quest, progress, QuestProgressData.get(player.getServer())));
        }
        return OperationResult.success("Objective force-completed without consuming resources");
    }

    /** The durable personal tracking record survives terminal lifecycle states and remains independent of team progress. */
    public String trackedQuest(ServerPlayer player) {
        QuestProgressData data = QuestProgressData.get(player.getServer());
        String tracked = data.tracked(player.getUUID());
        if (!tracked.isEmpty()) return tracked;
        if (!shared(player)) {
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
        boolean shared = shared(player);
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

    private boolean dependenciesComplete(QuestDefinition quest, PlayerProgress progress) {
        return queries.dependenciesComplete(quest, progress);
    }

    /** Preserves terminal history while allowing prerequisite edits and resets to demote stale availability. */
    static QuestStatus reconciledAvailability(QuestStatus current, boolean dependenciesSatisfied) {
        return ProgressQueries.reconciledAvailability(current, dependenciesSatisfied);
    }

    /** Interactive types are skipped by every automatic trigger, including restored completions. */
    private static boolean automaticAllowed(RewardDefinition reward) {
        var book = QuestBookManager.get().active().orElse(null);
        if (book == null || book.book().settings().suppressAutoClaim()) return false;
        var type = RewardTypeRegistry.get(reward.typeId());
        return reward.policy().automatic() && type != null && !type.requiresManualClaim(reward.config());
    }

    /** Personal completion is durable, but only the full roster can commit the shared quest. */
    private OperationResult finishMemberCompletion(ServerPlayer player, QuestDefinition quest,
                                                   PlayerProgress progress, QuestProgressData data) {
        boolean changed = progress.objectives(player.getUUID()).status(quest.id().toString()) != QuestStatus.COMPLETED;
        progress.objectives(player.getUUID()).status(quest.id().toString(), QuestStatus.COMPLETED);
        if (progress.allMembersCompleted(quest.id().toString(), ProgressOwnerService.resolve(player).orElseThrow().members())) {
            return markCompleted(player, quest, progress, data);
        }
        data.setDirty();
        effects.sync(player, true);
        return new OperationResult(changed ? yourscraft.jasdewstarfield.brnquest.api.OperationStatus.SUCCESS
                : yourscraft.jasdewstarfield.brnquest.api.OperationStatus.NO_CHANGE,
                "WAITING_FOR_TEAM", "Your objectives are complete; waiting for all team members");
    }

    private OperationResult markCompleted(ServerPlayer player, QuestDefinition quest, PlayerProgress progress, QuestProgressData data) {
        // Capture a legacy personal ACTIVE focus before committing the terminal lifecycle state.
        trackedQuest(player);
        progress.status(quest.id().toString(), QuestStatus.COMPLETED);
        if (shared(player)) progress.completionMembers(quest.id().toString(),
                ProgressOwnerService.resolve(player).orElseThrow().members());
        long completedAt = System.currentTimeMillis();
        progress.completedCycle(quest.id().toString(), completedAt,
                completedAt + quest.behavior().repeatCooldownSeconds() * 1000L);
        data.setDirty();
        sampling.prepareOwnerSamplesNow(player);
        effects.publish(new QuestCompletedEvent(player.getUUID(), player.getScoreboardName(), quest.bookId(),
                quest.id(), ApiViews.quest(quest), BrnQuestApi.getProgress(player, quest.id().toString()).orElseThrow()));
        // Automatic and manual rewards enter the same idempotent claim ledger;
        // only the trigger differs.
        quest.rewards().stream().filter(ProgressEngine::automaticAllowed)
                .forEach(reward -> claim(player, reward.id(), reward.policy().notifyPlayer()));
        // A task-only reset preserves claims; completing it again must not reopen already claimed rewards.
        if (!quest.rewards().isEmpty() && rewardsResolved(quest, progress)) {
            progress.status(quest.id().toString(), QuestStatus.REWARD_CLAIMED);
        }
        reconcile(player);
        // Newly unlocked holding objectives should be checked on the next inventory sample.
        yourscraft.jasdewstarfield.brnquest.task.ItemTaskMonitor.mark(player);
        effects.sync(player, true);
        return OperationResult.success("Quest completed");
    }

    private boolean advanceRepeatIfReady(QuestDefinition quest, PlayerProgress progress, long now) {
        if (!queries.repeatReady(quest, progress, now)) return false;
        progress.beginNextCycle(quest.id().toString(), quest.tasks().stream().map(task -> task.id().toString()).toList(),
                quest.rewards().stream().map(reward -> reward.id().toString()).toList(),
                dependenciesComplete(quest, progress) ? QuestStatus.AVAILABLE : QuestStatus.LOCKED);
        return true;
    }

    private boolean taskIsCurrent(ServerPlayer player, QuestDefinition quest, TaskDefinition task, PlayerProgress progress) {
        return queries.taskIsCurrent(player, quest, task, progress);
    }

    private boolean dependencyCompleted(ResourceLocation id, PlayerProgress progress) {
        return queries.dependencyCompleted(id, progress);
    }

    private boolean dependencyStarted(ResourceLocation id, PlayerProgress progress) {
        return queries.dependencyStarted(id, progress);
    }

    private boolean booleanConfig(Map<String, String> config, String key) {
        String value = config.getOrDefault(key, "false");
        return "true".equalsIgnoreCase(value) || "1b".equalsIgnoreCase(value);
    }

    private void restoreMainInventory(ServerPlayer player, List<net.minecraft.world.item.ItemStack> snapshot) {
        for (int index = 0; index < snapshot.size(); index++) player.getInventory().items.set(index, snapshot.get(index));
        player.getInventory().setChanged();
    }

    private long changeTaskProgress(ServerPlayer player, QuestDefinition quest, TaskDefinition task,
                                    PlayerProgress progress, long amount) {
        long previous = taskLedger(player, quest, progress).taskProgress(task.id().toString());
        long current = taskLedger(player, quest, progress).addTaskProgress(task.id().toString(), amount);
        if (previous != current) {
            // Mark the authoritative world data dirty before observers see the committed value.
            QuestProgressData.get(player.getServer()).setDirty();
            sampling.progressChanged(player, quest, progress);
            effects.publish(new TaskProgressChangedEvent(player.getUUID(), player.getScoreboardName(),
                    quest.bookId(), quest.id(), task.id(), ApiViews.task(task), previous, current));
        }
        return current;
    }

    private TaskContext taskContext(ServerPlayer player, QuestDefinition quest, TaskDefinition task,
                                    PlayerProgress progress) {
        return queries.taskContext(player, quest, task, progress);
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
