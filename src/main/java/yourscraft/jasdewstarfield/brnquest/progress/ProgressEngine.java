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
import yourscraft.jasdewstarfield.brnquest.task.TaskTypes;
import yourscraft.jasdewstarfield.brnquest.task.TaskSubmissionSelection;
import yourscraft.jasdewstarfield.brnquest.task.ItemChoiceMatcher;
import net.minecraft.world.item.ItemStack;

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

    private boolean shared(ServerPlayer player) {
        return !ProgressOwnerService.require(player).providerId().equals(
                yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerProviders.PERSONAL);
    }
    public boolean rewardClaimed(ServerPlayer player, RewardDefinition reward) {
        PlayerProgress progress = progress(player);
        return !shared(player) || reward.teamReward() ? progress.isClaimed(reward.id().toString())
                : progress.memberClaimed(player.getUUID(), reward.id().toString());
    }
    public Set<String> visibleClaims(ServerPlayer player) {
        PlayerProgress progress = progress(player);
        return shared(player) ? progress.claimsFor(player.getUUID()) : progress.claimedRewardsView();
    }
    public Map<String, QuestStatus> visibleStatuses(ServerPlayer player) {
        PlayerProgress progress = progress(player);
        if (!shared(player)) return progress.questsView();
        Map<String, QuestStatus> result = new HashMap<>(progress.questsView());
        String tracked = QuestProgressData.get(player.getServer()).tracked(player.getUUID());
        var snapshot = QuestBookManager.get().active().orElse(null);
        if (snapshot != null) for (QuestDefinition quest : snapshot.book().quests()) {
            String id = quest.id().toString();
            QuestStatus status = progress.status(id);
            if (status == QuestStatus.AVAILABLE || status == QuestStatus.ACTIVE) {
                result.put(id, id.equals(tracked) ? QuestStatus.ACTIVE : QuestStatus.AVAILABLE);
            } else if (status == QuestStatus.COMPLETED || status == QuestStatus.REWARD_CLAIMED) {
                result.put(id, !quest.rewards().isEmpty() && quest.rewards().stream()
                        .allMatch(reward -> rewardClaimed(player, reward)) ? QuestStatus.REWARD_CLAIMED : QuestStatus.COMPLETED);
            }
        }
        return Map.copyOf(result);
    }
    /** Repeat blocking uses the frozen cohort, including offline members, rather than the first claimant. */
    private boolean rewardsResolved(QuestDefinition quest, PlayerProgress progress) {
        Set<UUID> cohort = progress.completionMembers(quest.id().toString());
        return quest.rewards().stream().allMatch(reward -> cohort.isEmpty() || reward.teamReward()
                ? progress.isClaimed(reward.id().toString())
                : cohort.stream().allMatch(player -> progress.memberClaimed(player, reward.id().toString())));
    }
    /** Automatic delivery is retried idempotently for eligible online members; offline members wait for login. */
    private void deliverAutomatic(ServerPlayer player) {
        var snapshot = QuestBookManager.get().active().orElse(null);
        if (snapshot == null) return;
        for (QuestDefinition quest : snapshot.book().quests()) {
            QuestStatus status = progress(player).status(quest.id().toString());
            if (status == QuestStatus.COMPLETED || status == QuestStatus.REWARD_CLAIMED) {
                for (RewardDefinition reward : quest.rewards()) if (reward.policy().automatic()
                        && !rewardClaimed(player, reward)) claim(player, reward.id(), reward.policy().notifyPlayer());
            }
        }
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
        data.setDirty();
    }

    /** Performs time-based repeat reopening even when no inventory event occurs. */
    public void tick(ServerPlayer player) {
        // Server time is the stable cadence source; player tick counters can reset during lifecycle transitions.
        if (player.getServer().getTickCount() % 20 != 0) return;
        var snapshot = QuestBookManager.get().active().orElse(null);
        if (snapshot == null) return;
        if (shared(player)) deliverAutomatic(player);
        PlayerProgress progress = progress(player);
        boolean changed = false;
        long now = System.currentTimeMillis();
        for (QuestDefinition quest : snapshot.book().quests()) {
            changed |= advanceRepeatIfReady(quest, progress, now);
        }
        if (changed) {
            QuestProgressData.get(player.getServer()).setDirty();
            BrnQuestNetwork.syncProgress(player, true);
        }
    }

    /** Server-authored visibility set; clients render it but never infer hidden quest access. */
    public Set<String> visibleQuestIds(ServerPlayer player) {
        var snapshot = QuestBookManager.get().active().orElse(null);
        if (snapshot == null) return Set.of();
        PlayerProgress progress = progress(player);
        Map<ResourceLocation, Boolean> memo = new HashMap<>();
        Set<String> visible = new HashSet<>();
        snapshot.book().quests().forEach(quest -> {
            if (isVisible(quest, progress, snapshot.book(), memo)) visible.add(quest.id().toString());
        });
        return Set.copyOf(visible);
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
                if (type != null && !task.optional() && progress.taskProgress(task.id().toString()) < 1
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
        if (!dependenciesComplete(owner, progress) || !taskIsCurrent(player, owner, task, progress)) {
            return OperationResult.failure("LOCKED", "Task is not currently progressable");
        }
        changeTaskProgress(player, owner, task, progress, amount);
        QuestProgressData.get(player.getServer()).setDirty();
        BrnQuestNetwork.syncProgress(player, true);
        return OperationResult.success("Task progress updated");
    }

    /** Counts actual player crafting output for item objectives marked only_from_crafting. */
    public void recordCraft(ServerPlayer player, ItemStack crafted) {
        if (crafted.isEmpty()) return;
        synchronizedOwner(player, () -> {
            var snapshot = QuestBookManager.get().active().orElse(null);
            if (snapshot == null) return null;
            PlayerProgress progress = progress(player);
            for (QuestDefinition quest : snapshot.book().quests()) {
                QuestStatus status = progress.status(quest.id().toString());
                if ((status != QuestStatus.AVAILABLE && status != QuestStatus.ACTIVE)
                        || !dependenciesComplete(quest, progress)) continue;
                for (TaskDefinition task : quest.tasks()) {
                    if (!TaskTypes.ITEM.equals(task.typeId()) && !TaskTypes.ITEM_CHOICE.equals(task.typeId())) continue;
                    if (!booleanConfig(task.config(), "only_from_crafting") || !taskIsCurrent(player, quest, task, progress)) continue;
                    ItemChoiceMatcher.Spec spec = ItemChoiceMatcher.parseConfig(task.config()).result().orElse(null);
                    // A single progress counter cannot losslessly represent several independent alternatives.
                    if (spec == null || spec.entries().size() != 1 || spec.requiredEntries() != 1
                            || !ItemChoiceMatcher.accepts(player.registryAccess(), spec, crafted)) continue;
                    long required = spec.entries().stream().filter(entry -> acceptsEntry(player, entry, crafted))
                            .mapToLong(ItemChoiceMatcher.Entry::requiredCount).min().orElse(1L);
                    long previous = progress.taskProgress(task.id().toString());
                    if (previous < required) changeTaskProgress(player, quest, task, progress,
                            Math.min((long) crafted.getCount(), required - previous));
                    if (progress.taskProgress(task.id().toString()) >= required) {
                        complete(player, quest.id(), false);
                        break;
                    }
                }
            }
            QuestProgressData.get(player.getServer()).setDirty();
            BrnQuestNetwork.syncProgress(player, true);
            return null;
        });
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
            if (rewardClaimed(player, reward)) return OperationResult.noChange("ALREADY_CLAIMED", "Reward already claimed");
            if (shared(player) && !progress.completionMembers(owner.id().toString()).contains(player.getUUID())) {
                return OperationResult.failure("NOT_ELIGIBLE", "Player was not a member when this cycle completed");
            }
            RewardType<?> type = RewardTypeRegistry.get(reward.typeId());
            if (type == null) return OperationResult.failure("UNKNOWN_TYPE", "Unknown reward type");
            // The ledger is persisted before the non-repeatable side effect to prevent crash duplication.
            if (shared(player) && !reward.teamReward()) progress.claimMember(player.getUUID(), rewardId.toString());
            else progress.claim(rewardId.toString());
            QuestProgressData data = QuestProgressData.get(player.getServer());
            data.setDirty();
            var result = RewardTypeExecutor.execute(type, new RewardContext(player, owner.bookId(), owner.id(),
                    ApiViews.reward(reward)));
            if (!result.success()) return OperationResult.failure("EXECUTION_FAILED", result.message());
            if (rewardsResolved(owner, progress)) progress.status(owner.id().toString(), QuestStatus.REWARD_CLAIMED);
            BrnQuestEvents.post(new RewardClaimedEvent(player.getUUID(), player.getScoreboardName(),
                    snapshot.book().id(), owner.id(), rewardId, ApiViews.reward(reward),
                    BrnQuestApi.getProgress(player, owner.id().toString()).orElseThrow()));
            advanceRepeatIfReady(owner, progress, System.currentTimeMillis());
            BrnQuestNetwork.syncProgress(player, true);
            if (notifyAutomatic) {
                player.displayClientMessage(Component.translatable("message.brnquest.reward.auto_claimed",
                        rewardId.toString()), true);
            }
            return OperationResult.success(result.message());
        });
    }

    public void reset(ServerPlayer player, ResourceLocation questId) {
        synchronizedOwner(player, () -> {
            var quest = QuestBookManager.get().active().map(s -> s.quests().get(questId)).orElse(null);
            if (quest == null) return null;
            progress(player).resetQuest(questId.toString(), quest.tasks().stream().map(t -> t.id().toString()).toList(),
                    quest.rewards().stream().map(r -> r.id().toString()).toList());
            QuestProgressData.get(player.getServer()).setDirty();
            reconcile(player);
            BrnQuestNetwork.syncProgress(player, false);
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
            long previous = progress.taskProgress(taskId.toString());
            progress.resetTask(quest.id().toString(), taskId.toString(),
                    dependenciesComplete(quest, progress) ? QuestStatus.AVAILABLE : QuestStatus.LOCKED);
            QuestProgressData.get(player.getServer()).setDirty();
            if (previous != 0) BrnQuestEvents.post(new TaskProgressChangedEvent(player.getUUID(),
                    player.getScoreboardName(), quest.bookId(), quest.id(), taskId, ApiViews.task(task), previous, 0));
            // A task reset can invalidate descendants, so recompute the owner graph before synchronization.
            reconcile(player);
            return OperationResult.success("Objective progress reset; reward claims preserved");
        }
        if (progress.taskProgress(taskId.toString()) >= 1) {
            return OperationResult.noChange("ALREADY_SUBMITTED", "Objective already completed");
        }
        // Do not call the normal submission path: it may consume other currently satisfied items.
        changeTaskProgress(player, quest, task, progress, Long.MAX_VALUE / 4);
        QuestStatus status = progress.status(quest.id().toString());
        if (status != QuestStatus.COMPLETED && status != QuestStatus.REWARD_CLAIMED
                && dependenciesComplete(quest, progress)
                && quest.tasks().stream().filter(value -> !value.optional())
                .allMatch(value -> progress.taskProgress(value.id().toString()) >= 1)) {
            return boundedCompletion(() -> markCompleted(player, quest, progress, QuestProgressData.get(player.getServer())));
        }
        return OperationResult.success("Objective force-completed without consuming resources");
    }

    public OperationResult toggleTracked(ServerPlayer player, ResourceLocation questId) {
        PlayerProgress progress = progress(player);
        QuestStatus status = progress.status(questId.toString());
        if (status != QuestStatus.AVAILABLE && status != QuestStatus.ACTIVE) return OperationResult.failure("NOT_TRACKABLE", "Quest is not available");
        if (shared(player)) {
            QuestProgressData data = QuestProgressData.get(player.getServer());
            data.tracked(player.getUUID(), questId.toString().equals(data.tracked(player.getUUID())) ? "" : questId.toString());
            BrnQuestNetwork.syncProgress(player, false);
            return OperationResult.success("Tracking updated");
        }
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
        if (quest.dependencies().isEmpty()) return true;
        long completed = quest.dependencies().stream().filter(id -> dependencyCompleted(id, progress)).count();
        long started = quest.dependencies().stream().filter(id -> dependencyStarted(id, progress)).count();
        return QuestDependencyEvaluator.satisfied(quest.behavior().dependencyRequirement(),
                quest.behavior().minimumRequiredDependencies(), quest.dependencies().size(), completed, started);
    }

    /** Preserves terminal history while allowing prerequisite edits and resets to demote stale availability. */
    static QuestStatus reconciledAvailability(QuestStatus current, boolean dependenciesSatisfied) {
        if (current == QuestStatus.COMPLETED || current == QuestStatus.REWARD_CLAIMED) return current;
        if (!dependenciesSatisfied) return QuestStatus.LOCKED;
        return current == QuestStatus.ACTIVE ? QuestStatus.ACTIVE : QuestStatus.AVAILABLE;
    }

    private OperationResult markCompleted(ServerPlayer player, QuestDefinition quest, PlayerProgress progress, QuestProgressData data) {
        progress.status(quest.id().toString(), QuestStatus.COMPLETED);
        if (shared(player)) progress.completionMembers(quest.id().toString(),
                ProgressOwnerService.resolve(player).orElseThrow().members());
        long completedAt = System.currentTimeMillis();
        progress.completedCycle(quest.id().toString(), completedAt,
                completedAt + quest.behavior().repeatCooldownSeconds() * 1000L);
        data.setDirty();
        BrnQuestEvents.post(new QuestCompletedEvent(player.getUUID(), player.getScoreboardName(), quest.bookId(),
                quest.id(), ApiViews.quest(quest), BrnQuestApi.getProgress(player, quest.id().toString()).orElseThrow()));
        // Automatic and manual rewards enter the same idempotent claim ledger;
        // only the trigger differs.
        quest.rewards().stream().filter(reward -> reward.policy().automatic())
                .forEach(reward -> claim(player, reward.id(), reward.policy().notifyPlayer()));
        // A task-only reset preserves claims; completing it again must not reopen already claimed rewards.
        if (!quest.rewards().isEmpty() && rewardsResolved(quest, progress)) {
            progress.status(quest.id().toString(), QuestStatus.REWARD_CLAIMED);
        }
        reconcile(player);
        BrnQuestNetwork.syncProgress(player, true);
        return OperationResult.success("Quest completed");
    }

    private boolean advanceRepeatIfReady(QuestDefinition quest, PlayerProgress progress, long now) {
        if (!quest.behavior().repeatable()) return false;
        QuestStatus status = progress.status(quest.id().toString());
        if (status != QuestStatus.COMPLETED && status != QuestStatus.REWARD_CLAIMED) return false;
        boolean rewardsResolved = quest.behavior().ignoreRewardBlocking() || quest.rewards().isEmpty()
                || rewardsResolved(quest, progress);
        if (!rewardsResolved || now < progress.nextAvailableAt(quest.id().toString())) return false;
        progress.beginNextCycle(quest.id().toString(), quest.tasks().stream().map(task -> task.id().toString()).toList(),
                quest.rewards().stream().map(reward -> reward.id().toString()).toList(),
                dependenciesComplete(quest, progress) ? QuestStatus.AVAILABLE : QuestStatus.LOCKED);
        return true;
    }

    private boolean taskIsCurrent(ServerPlayer player, QuestDefinition quest, TaskDefinition task, PlayerProgress progress) {
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

    private boolean dependencyCompleted(ResourceLocation id, PlayerProgress progress) {
        QuestStatus status = progress.status(id.toString());
        return status == QuestStatus.COMPLETED || status == QuestStatus.REWARD_CLAIMED
                || progress.completionCycles(id.toString()) > 0;
    }

    private boolean dependencyStarted(ResourceLocation id, PlayerProgress progress) {
        QuestStatus status = progress.status(id.toString());
        if (status == QuestStatus.ACTIVE || dependencyCompleted(id, progress)) return true;
        QuestDefinition quest = QuestBookManager.get().active().map(snapshot -> snapshot.quests().get(id)).orElse(null);
        return quest != null && quest.tasks().stream().anyMatch(task -> progress.taskProgress(task.id().toString()) > 0);
    }

    private boolean isVisible(QuestDefinition quest, PlayerProgress progress, QuestBookDefinition book,
                              Map<ResourceLocation, Boolean> memo) {
        Boolean cached = memo.get(quest.id());
        if (cached != null) return cached;
        memo.put(quest.id(), false); // validated DAG guard
        QuestStatus status = progress.status(quest.id().toString());
        boolean complete = status == QuestStatus.COMPLETED || status == QuestStatus.REWARD_CLAIMED
                || progress.completionCycles(quest.id().toString()) > 0;
        long completedTasks = quest.tasks().stream().filter(task -> progress.taskProgress(task.id().toString()) >= 1).count();
        if (quest.behavior().invisibleUntilComplete() && !complete
                && (quest.behavior().visibleAfterTasks() <= 0
                || completedTasks < quest.behavior().visibleAfterTasks())) return false;
        if (quest.behavior().hideUntilDependenciesComplete() && !dependenciesComplete(quest, progress)) return false;
        if (quest.behavior().hideUntilDependenciesVisible()) for (ResourceLocation dependency : quest.dependencies()) {
            QuestDefinition parent = book.quests().stream().filter(value -> value.id().equals(dependency)).findFirst().orElse(null);
            if (parent != null && !isVisible(parent, progress, book, memo)) return false;
        }
        memo.put(quest.id(), true);
        return true;
    }

    private boolean acceptsEntry(ServerPlayer player, ItemChoiceMatcher.Entry entry, ItemStack stack) {
        try {
            ItemChoiceMatcher.Spec one = new ItemChoiceMatcher.Spec(List.of(entry), 1);
            return ItemChoiceMatcher.accepts(player.registryAccess(), one, stack);
        } catch (IllegalArgumentException ignored) { return false; }
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
