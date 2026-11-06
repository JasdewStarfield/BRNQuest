package yourscraft.jasdewstarfield.brnquest.progress;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.api.ApiViews;
import yourscraft.jasdewstarfield.brnquest.api.BrnQuestApi;
import yourscraft.jasdewstarfield.brnquest.api.OperationResult;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.data.RewardDefinition;
import yourscraft.jasdewstarfield.brnquest.data.TaskDefinition;
import yourscraft.jasdewstarfield.brnquest.event.QuestCompletedEvent;
import yourscraft.jasdewstarfield.brnquest.event.TaskProgressChangedEvent;
import yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerService;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;
import yourscraft.jasdewstarfield.brnquest.task.TaskType;
import yourscraft.jasdewstarfield.brnquest.task.TaskContext;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypeExecutor;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypeRegistry;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/** Applies completion, roster, reset and repeat transitions through protected engine commands. */
final class ProgressCompletion {
    interface Host {
        long changeTaskProgress(ServerPlayer player, QuestDefinition quest, TaskDefinition task, PlayerProgress progress, long amount);
        String trackedQuest(ServerPlayer player);
        void prepareOwnerSamples(ServerPlayer player);
        void prepareOwnerSamplesNow(ServerPlayer player);
        void resetTransientStates(ServerPlayer player, QuestDefinition quest, List<TaskDefinition> tasks);
        void restoreMainInventory(ServerPlayer player, List<ItemStack> inventory);
        void reconcile(ServerPlayer player);
        void deliverCompleted(ServerPlayer player, QuestDefinition quest);
        OperationResult forceComplete(ServerPlayer player, ResourceLocation questId);
        void reset(ServerPlayer player, ResourceLocation questId);
        OperationResult completeWithinBudget(java.util.function.Supplier<OperationResult> operation);
    }
    private final ProgressQueries queries;
    private final ProgressEffects effects;
    private final Host host;

    ProgressCompletion(ProgressQueries queries, ProgressEffects effects, Host host) {
        this.queries = queries; this.effects = effects; this.host = host;
    }

    /** The caller holds the owner lock and the completion budget across validation, consumption and commit. */
    OperationResult completeLocked(ServerPlayer player, ResourceLocation questId, boolean checkmarkIntent) {
        var snapshot = QuestBookManager.get().active().orElse(null);
        QuestDefinition quest = snapshot == null ? null : snapshot.quests().get(questId);
        if (quest == null) return OperationResult.failure("NOT_FOUND", "Unknown quest " + questId);
        QuestProgressData data = QuestProgressData.get(player.getServer());
        PlayerProgress progress = data.get(ProgressOwnerService.require(player));
        QuestStatus status = progress.status(questId.toString());
        if (status == QuestStatus.COMPLETED || status == QuestStatus.REWARD_CLAIMED) return OperationResult.noChange("ALREADY_COMPLETED", "Quest already completed");
        if (!queries.dependenciesComplete(quest, progress)) return OperationResult.failure("LOCKED", "Quest dependencies are incomplete");
        // An empty authoring placeholder has no completion condition; keep its progress untouched until configured.
        if (quest.tasks().isEmpty()) return OperationResult.failure("NO_OBJECTIVES", "Quest has no objectives configured");
        if (queries.allMembers(player, quest)
                && progress.objectives(player.getUUID()).status(questId.toString()) == QuestStatus.COMPLETED) {
            return finishMemberCompletion(player, quest, progress, data);
        }
        if (checkmarkIntent) {
            // Quest-wide completion remains a generic intent; each task type decides whether it accepts it.
            for (TaskDefinition task : quest.tasks()) {
                TaskType<?> type = TaskTypeRegistry.get(task.typeId());
                TaskContext context = queries.taskContext(player, quest, task, progress);
                if (type != null && queries.taskIsCurrent(player, quest, task, progress)
                        && TaskTypeExecutor.acceptsQuestCompletionIntent(type, context.task())
                        && TaskTypeExecutor.submit(type, context).success()) {
                    host.changeTaskProgress(player, quest, task, progress, 1);
                    // Sequential mode represents one author-ordered step per user intent.
                    if (quest.behavior().sequentialTasks()) break;
                }
            }
        }
        for (TaskDefinition task : quest.tasks()) {
            TaskType<?> type = TaskTypeRegistry.get(task.typeId());
            if (type == null || (!task.optional() && !TaskTypeExecutor.satisfied(type,
                    queries.taskContext(player, quest, task, progress)))) {
                return OperationResult.failure("UNSATISFIED", "Task is incomplete: " + task.id());
            }
        }
        List<net.minecraft.world.item.ItemStack> inventoryBeforeConsume = player.getInventory().items.stream()
                .map(net.minecraft.world.item.ItemStack::copy).toList();
        for (TaskDefinition task : quest.tasks()) {
            TaskType<?> type = TaskTypeRegistry.get(task.typeId());
            // Submitted rows already consumed once; unsubmitted optional rows must not be
            // swept into another row's completion transaction or spend the player's items.
            if (type != null && !task.optional() && queries.taskLedger(player, quest, progress).taskProgress(task.id().toString()) < 1
                    && !TaskTypeExecutor.consume(type, queries.taskContext(player, quest, task, progress))) {
                host.restoreMainInventory(player, inventoryBeforeConsume);
                return OperationResult.failure("CONSUME_FAILED", "Could not consume task items");
            }
        }
        if (queries.allMembers(player, quest)) {
            // Inventory-satisfied objectives need a receipt before resources disappear or the player logs out.
            for (var task : quest.tasks()) if (!task.optional()
                    && queries.taskLedger(player, quest, progress).taskProgress(task.id().toString()) == 0) {
                host.changeTaskProgress(player, quest, task, progress, 1);
            }
            data.setDirty();
            return finishMemberCompletion(player, quest, progress, data);
        }
        return markCompleted(player, quest, progress, data);
    }

    OperationResult forceCompleteLocked(ServerPlayer player, ResourceLocation questId) {
        var quest = QuestBookManager.get().active().map(s -> s.quests().get(questId)).orElse(null);
        if (quest == null) return OperationResult.failure("NOT_FOUND", "Unknown quest");
        QuestProgressData data = QuestProgressData.get(player.getServer());
        PlayerProgress progress = data.get(ProgressOwnerService.require(player));
        QuestStatus status = progress.status(questId.toString());
        if (status == QuestStatus.COMPLETED || status == QuestStatus.REWARD_CLAIMED) return OperationResult.noChange("ALREADY_COMPLETED", "Quest already completed");
        // Administrative/API completion intentionally bypasses task resources;
        // callers have already made an explicit server-side authority decision.
        quest.tasks().forEach(task -> {
            if (queries.taskLedger(player, quest, progress).taskProgress(task.id().toString()) < 1) {
                host.changeTaskProgress(player, quest, task, progress, Long.MAX_VALUE / 4);
            }
        });
        return markCompleted(player, quest, progress, data);
    }

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

    /** Publish after committing the cycle and successor baselines to the owner ledger; rewards follow the event. */
    OperationResult markCompleted(ServerPlayer player, QuestDefinition quest, PlayerProgress progress, QuestProgressData data) {
        // Capture a legacy personal ACTIVE focus before committing the terminal lifecycle state.
        host.trackedQuest(player);
        progress.status(quest.id().toString(), QuestStatus.COMPLETED);
        if (queries.shared(player)) progress.completionMembers(quest.id().toString(),
                ProgressOwnerService.resolve(player).orElseThrow().members());
        long completedAt = System.currentTimeMillis();
        progress.completedCycle(quest.id().toString(), completedAt,
                completedAt + quest.behavior().repeatCooldownSeconds() * 1000L);
        data.setDirty();
        host.prepareOwnerSamplesNow(player);
        effects.publish(new QuestCompletedEvent(player.getUUID(), player.getScoreboardName(), quest.bookId(),
                quest.id(), ApiViews.quest(quest), BrnQuestApi.getProgress(player, quest.id().toString()).orElseThrow()));
        // Automatic and manual rewards enter the same idempotent claim ledger;
        // only the trigger differs.
        host.deliverCompleted(player, quest);
        // A task-only reset preserves claims; completing it again must not reopen already claimed rewards.
        if (!quest.rewards().isEmpty() && queries.rewardsResolved(quest, progress)) {
            progress.status(quest.id().toString(), QuestStatus.REWARD_CLAIMED);
        }
        host.reconcile(player);
        // Newly unlocked holding objectives should be checked on the next inventory sample.
        yourscraft.jasdewstarfield.brnquest.task.ItemTaskMonitor.mark(player);
        effects.sync(player, true);
        return OperationResult.success("Quest completed");
    }

    /** Readiness is a query; clearing counters and receipts is one explicit cycle transition. */
    boolean advanceRepeatIfReady(QuestDefinition quest, PlayerProgress progress, long now) {
        if (!queries.repeatReady(quest, progress, now)) return false;
        progress.beginNextCycle(quest.id().toString(), quest.tasks().stream().map(task -> task.id().toString()).toList(),
                quest.rewards().stream().map(reward -> reward.id().toString()).toList(),
                queries.dependenciesComplete(quest, progress) ? QuestStatus.AVAILABLE : QuestStatus.LOCKED);
        return true;
    }

    void resetLocked(ServerPlayer player, ResourceLocation questId) {
        var quest = QuestBookManager.get().active().map(s -> s.quests().get(questId)).orElse(null);
        if (quest == null) return;
        queries.progress(player).resetQuest(questId.toString(), quest.tasks().stream().map(t -> t.id().toString()).toList(),
                quest.rewards().stream().map(r -> r.id().toString()).toList());
        host.resetTransientStates(player, quest, quest.tasks());
        QuestProgressData.get(player.getServer()).setDirty();
        host.reconcile(player);
        effects.sync(player, false);
    }

    OperationResult administerLocked(ServerPlayer player, QuestDefinition quest, ResourceLocation taskId,
                               AdminProgressAction action) {
        PlayerProgress progress = queries.progress(player);
        if (action == AdminProgressAction.FORCE_QUEST) return host.forceComplete(player, quest.id());
        if (action == AdminProgressAction.RESET_QUEST) {
            host.reset(player, quest.id());
            return OperationResult.success("Quest progress reset");
        }
        TaskDefinition task = quest.tasks().stream().filter(value -> value.id().equals(taskId)).findFirst().orElseThrow();
        if (action == AdminProgressAction.RESET_TASK) {
            long previous = queries.taskLedger(player, quest, progress).taskProgress(taskId.toString());
            progress.resetTask(quest.id().toString(), taskId.toString(),
                    queries.dependenciesComplete(quest, progress) ? QuestStatus.AVAILABLE : QuestStatus.LOCKED);
            QuestProgressData.get(player.getServer()).setDirty();
            host.resetTransientStates(player, quest, List.of(task));
            if (previous != 0) effects.publish(new TaskProgressChangedEvent(player.getUUID(),
                    player.getScoreboardName(), quest.bookId(), quest.id(), taskId, ApiViews.task(task), previous, 0));
            // A task reset can invalidate descendants, so recompute the owner graph before synchronization.
            host.reconcile(player);
            return OperationResult.success("Objective progress reset; reward claims preserved");
        }
        if (queries.taskLedger(player, quest, progress).taskProgress(taskId.toString()) >= 1) {
            return OperationResult.noChange("ALREADY_SUBMITTED", "Objective already completed");
        }
        // Do not call the normal submission path: it may consume other currently satisfied items.
        host.changeTaskProgress(player, quest, task, progress, Long.MAX_VALUE / 4);
        QuestStatus status = progress.status(quest.id().toString());
        if (status != QuestStatus.COMPLETED && status != QuestStatus.REWARD_CLAIMED
                && queries.dependenciesComplete(quest, progress)
                && quest.tasks().stream().filter(value -> !value.optional())
                .allMatch(value -> queries.taskLedger(player, quest, progress).taskProgress(value.id().toString()) >= 1)) {
            if (queries.allMembers(player, quest)) {
                return host.completeWithinBudget(() -> finishMemberCompletion(player, quest, progress, QuestProgressData.get(player.getServer())));
            }
            return host.completeWithinBudget(() -> markCompleted(player, quest, progress, QuestProgressData.get(player.getServer())));
        }
        return OperationResult.success("Objective force-completed without consuming resources");
    }

    void reconcile(ServerPlayer player) {
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
            QuestStatus reconciled = ProgressQueries.reconciledAvailability(existing, queries.dependenciesComplete(quest, progress));
            if (reconciled != existing) progress.status(quest.id().toString(), reconciled);
        }
        progress.questsView().keySet().stream().filter(id -> !current.contains(id)).forEach(progress::orphan);
        progress.revision(snapshot.revision());
        host.prepareOwnerSamples(player);
        data.setDirty();
    }

    private static ResourceLocation typedLegacyId(String key, String prefix) {
        return key.startsWith(prefix) ? ResourceLocation.tryParse(key.substring(prefix.length())) : null;
    }

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

}
