package yourscraft.jasdewstarfield.brnquest.progress;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.api.ApiViews;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.data.RewardDefinition;
import yourscraft.jasdewstarfield.brnquest.data.TaskDefinition;
import yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerService;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;
import yourscraft.jasdewstarfield.brnquest.task.TaskType;
import yourscraft.jasdewstarfield.brnquest.task.TaskContext;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypeExecutor;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypeRegistry;

import java.util.*;

/** Read-side owner projections and completion policies; never publishes events or sends progress. */
final class ProgressQueries {
    private volatile QuestVisibilityPlan visibilityPlan;

    PlayerProgress progress(ServerPlayer player) {
        return QuestProgressData.get(player.getServer()).get(ProgressOwnerService.require(player));
    }

    boolean allMembers(ServerPlayer player, QuestDefinition quest) {
        return quest.behavior().requireAllTeamMembers() && shared(player);
    }

    PlayerProgress taskLedger(ServerPlayer player, QuestDefinition quest, PlayerProgress progress) {
        return allMembers(player, quest) ? progress.objectives(player.getUUID()) : progress;
    }

    long taskProgress(ServerPlayer player, QuestDefinition quest, TaskDefinition task) {
        return taskLedger(player, quest, progress(player)).taskProgress(task.id().toString());
    }

    Map<String, Long> visibleTaskProgress(ServerPlayer player) {
        PlayerProgress progress = progress(player);
        Map<String, Long> result = new HashMap<>(progress.taskProgressView());
        var snapshot = QuestBookManager.get().active().orElse(null);
        if (snapshot != null && shared(player)) {
            var personal = progress.objectives(player.getUUID());
            for (var quest : snapshot.book().quests()) if (quest.behavior().requireAllTeamMembers()) {
                for (var task : quest.tasks()) result.put(task.id().toString(), personal.taskProgress(task.id().toString()));
            }
        }
        return Map.copyOf(result);
    }

    Set<String> waitingForTeam(ServerPlayer player) {
        var snapshot = QuestBookManager.get().active().orElse(null);
        if (snapshot == null || !shared(player)) return Set.of();
        var progress = progress(player);
        return snapshot.book().quests().stream().filter(quest -> quest.behavior().requireAllTeamMembers())
                .filter(quest -> progress.status(quest.id().toString()) == QuestStatus.AVAILABLE
                        || progress.status(quest.id().toString()) == QuestStatus.ACTIVE)
                .filter(quest -> progress.objectives(player.getUUID()).status(quest.id().toString()) == QuestStatus.COMPLETED)
                .map(quest -> quest.id().toString()).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    boolean shared(ServerPlayer player) {
        return !ProgressOwnerService.require(player).providerId().equals(
                yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerProviders.PERSONAL);
    }

    boolean rewardClaimed(ServerPlayer player, RewardDefinition reward) {
        PlayerProgress progress = progress(player);
        return !shared(player) || reward.teamReward() ? progress.isClaimed(reward.id().toString())
                : progress.memberClaimed(player.getUUID(), reward.id().toString());
    }

    Set<String> visibleClaims(ServerPlayer player) {
        PlayerProgress progress = progress(player);
        return shared(player) ? progress.claimsFor(player.getUUID()) : progress.claimedRewardsView();
    }

    Map<String, QuestStatus> visibleStatuses(ServerPlayer player) {
        PlayerProgress progress = progress(player);
        if (!shared(player)) return progress.questsView();
        // Resolve the viewer ledger once; ordinary receipts remain specific to this member.
        Map<String, QuestStatus> result = new HashMap<>(progress.questsView());
        String tracked = QuestProgressData.get(player.getServer()).tracked(player.getUUID());
        var snapshot = QuestBookManager.get().active().orElse(null);
        if (snapshot != null) for (QuestDefinition quest : snapshot.quests().values()) {
            String id = quest.id().toString();
            QuestStatus status = progress.status(id);
            if (status == QuestStatus.AVAILABLE || status == QuestStatus.ACTIVE) {
                result.put(id, id.equals(tracked) ? QuestStatus.ACTIVE : QuestStatus.AVAILABLE);
            } else if (status == QuestStatus.COMPLETED || status == QuestStatus.REWARD_CLAIMED) {
                result.put(id, !quest.rewards().isEmpty() && quest.rewards().stream()
                        .allMatch(reward -> reward.teamReward() ? progress.isClaimed(reward.id().toString())
                                : progress.memberClaimed(player.getUUID(), reward.id().toString()))
                        ? QuestStatus.REWARD_CLAIMED : QuestStatus.COMPLETED);
            }
        }
        return Map.copyOf(result);
    }

    boolean rewardsResolved(QuestDefinition quest, PlayerProgress progress) {
        Set<UUID> cohort = progress.completionMembers(quest.id().toString());
        return quest.rewards().stream().allMatch(reward -> cohort.isEmpty() || reward.teamReward()
                ? progress.isClaimed(reward.id().toString())
                : cohort.stream().allMatch(player -> progress.memberClaimed(player, reward.id().toString())));
    }

    Set<String> visibleQuestIds(ServerPlayer player) {
        var snapshot = QuestBookManager.get().active().orElse(null);
        if (snapshot == null) return Set.of();
        QuestVisibilityPlan plan = visibilityPlan;
        // Same-ID edits/reloads replace the snapshot and must rebuild even if the revision string is reused.
        if (plan == null || plan.snapshot != snapshot) {
            plan = new QuestVisibilityPlan(snapshot);
            visibilityPlan = plan;
        }
        PlayerProgress progress = progress(player);
        var tasks = visibleTaskProgress(player);
        return plan.visible(progress, quest -> dependenciesComplete(quest, progress),
                task -> tasks.getOrDefault(task.id().toString(), 0L));
    }

    boolean dependenciesComplete(QuestDefinition quest, PlayerProgress progress) {
        if (quest.dependencies().isEmpty()) return true;
        long completed = quest.dependencies().stream().filter(id -> dependencyCompleted(id, progress)).count();
        long started = quest.dependencies().stream().filter(id -> dependencyStarted(id, progress)).count();
        return QuestDependencyEvaluator.satisfied(quest.behavior().dependencyRequirement(),
                quest.behavior().minimumRequiredDependencies(), quest.dependencies().size(), completed, started);
    }

    static QuestStatus reconciledAvailability(QuestStatus current, boolean dependenciesSatisfied) {
        if (current == QuestStatus.COMPLETED || current == QuestStatus.REWARD_CLAIMED) return current;
        if (!dependenciesSatisfied) return QuestStatus.LOCKED;
        return current == QuestStatus.ACTIVE ? QuestStatus.ACTIVE : QuestStatus.AVAILABLE;
    }

    boolean taskIsCurrent(ServerPlayer player, QuestDefinition quest, TaskDefinition task, PlayerProgress progress) {
        if (allMembers(player, quest)
                && progress.objectives(player.getUUID()).status(quest.id().toString()) == QuestStatus.COMPLETED) return false;
        if (!quest.behavior().sequentialTasks() || task.optional()) return true;
        for (TaskDefinition candidate : quest.tasks()) {
            if (candidate.id().equals(task.id())) return true;
            if (!candidate.optional()) {
                TaskType<?> type = TaskTypeRegistry.get(candidate.typeId());
                if (type == null || !TaskTypeExecutor.satisfied(type, taskContext(player, quest, candidate, progress))) return false;
            }
        }
        return false;
    }

    boolean dependencyCompleted(ResourceLocation id, PlayerProgress progress) {
        QuestStatus status = progress.status(id.toString());
        return status == QuestStatus.COMPLETED || status == QuestStatus.REWARD_CLAIMED
                || progress.completionCycles(id.toString()) > 0;
    }

    boolean dependencyStarted(ResourceLocation id, PlayerProgress progress) {
        QuestStatus status = progress.status(id.toString());
        if (status == QuestStatus.ACTIVE || dependencyCompleted(id, progress)) return true;
        QuestDefinition quest = QuestBookManager.get().active().map(snapshot -> snapshot.quests().get(id)).orElse(null);
        return quest != null && quest.tasks().stream().anyMatch(task -> progress.taskStarted(task.id().toString()));
    }

    TaskContext taskContext(ServerPlayer player, QuestDefinition quest, TaskDefinition task,
                                    PlayerProgress progress) {
        return new TaskContext(player, quest.bookId(), quest.id(), ApiViews.task(task),
                taskLedger(player, quest, progress).taskProgress(task.id().toString()));
    }

    /** Frozen completion members, including offline recipients, must resolve rewards before a repeat. */
    boolean repeatReady(QuestDefinition quest, PlayerProgress progress, long now) {
        if (!quest.behavior().repeatable()) return false;
        QuestStatus status = progress.status(quest.id().toString());
        if (status != QuestStatus.COMPLETED && status != QuestStatus.REWARD_CLAIMED) return false;
        boolean resolved = quest.behavior().ignoreRewardBlocking() || quest.rewards().isEmpty()
                || rewardsResolved(quest, progress);
        return resolved && now >= progress.nextAvailableAt(quest.id().toString());
    }
}
