package yourscraft.jasdewstarfield.brnquest.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.BRNQuest;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestIds;
import yourscraft.jasdewstarfield.brnquest.network.BrnQuestNetwork;
import yourscraft.jasdewstarfield.brnquest.progress.ProgressEngine;
import yourscraft.jasdewstarfield.brnquest.progress.QuestStatus;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/** Public server API. Returned definitions and progress projections are immutable snapshots. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public final class BrnQuestApi {
    private static final OperationContext LEGACY_CONTEXT = OperationContext.integration(
            ResourceLocation.fromNamespaceAndPath("brnquest", "legacy_java_api"));

    private BrnQuestApi() {}

    public static boolean completeQuest(ServerPlayer player, String questId) {
        return completeQuestResult(player, questId).success();
    }

    public static OperationResult completeQuestResult(ServerPlayer player, String questId) {
        return completeQuestResult(LEGACY_CONTEXT, player, questId);
    }

    public static OperationResult completeQuestResult(OperationContext context, ServerPlayer player, String questId) {
        return execute(context, player, questId, "quest",
                (target, id) -> ProgressEngine.get().forceComplete(target, id));
    }

    /** Applies a normal quest-row intent without bypassing task requirements. */
    public static OperationResult submitQuestCompletionResult(OperationContext context, ServerPlayer player,
                                                              String questId, boolean checkmarkIntent) {
        return execute(context, player, questId, "quest_intent",
                (target, id) -> ProgressEngine.get().complete(target, id, checkmarkIntent));
    }

    public static OperationResult completeTaskResult(ServerPlayer player, String questId, String taskId) {
        return completeTaskResult(LEGACY_CONTEXT, player, questId, taskId);
    }

    public static OperationResult completeTaskResult(OperationContext context, ServerPlayer player,
                                                     String questId, String taskId) {
        OperationResult readiness = validateWriteContext(context, player);
        if (readiness != null) return audited(context, player, "complete_task", taskId, readiness);
        Optional<ResourceLocation> resolvedQuest = resolve(questId);
        Optional<ResourceLocation> resolvedTask = resolve(taskId);
        if (resolvedQuest.isEmpty()) return audited(context, player, "complete_task", questId, invalidId("quest", questId));
        if (resolvedTask.isEmpty()) return audited(context, player, "complete_task", taskId, invalidId("task", taskId));
        return audited(context, player, "complete_task", taskId,
                ProgressEngine.get().completeTask(player, resolvedQuest.orElseThrow(), resolvedTask.orElseThrow()));
    }

    public static boolean isQuestCompleted(ServerPlayer player, String questId) {
        return getProgress(player, questId)
                .map(ProgressView::status)
                .map(status -> status == QuestStatus.COMPLETED || status == QuestStatus.REWARD_CLAIMED)
                .orElse(false);
    }

    public static boolean addTaskProgress(ServerPlayer player, String taskId, long amount) {
        return addTaskProgressResult(player, taskId, amount).success();
    }

    public static OperationResult addTaskProgressResult(ServerPlayer player, String taskId, long amount) {
        return addTaskProgressResult(LEGACY_CONTEXT, player, taskId, amount);
    }

    public static OperationResult addTaskProgressResult(OperationContext context, ServerPlayer player,
                                                        String taskId, long amount) {
        if (amount <= 0) return audited(context, player, "task", taskId,
                OperationResult.invalid("INVALID_AMOUNT", "Amount must be positive"));
        return execute(context, player, taskId, "task",
                (target, id) -> ProgressEngine.get().addTaskProgress(target, id, amount));
    }

    public static boolean claimReward(ServerPlayer player, String rewardId) {
        return claimRewardResult(player, rewardId).success();
    }

    public static OperationResult claimRewardResult(ServerPlayer player, String rewardId) {
        return claimRewardResult(LEGACY_CONTEXT, player, rewardId);
    }

    public static OperationResult claimRewardResult(OperationContext context, ServerPlayer player, String rewardId) {
        return execute(context, player, rewardId, "reward", (target, id) -> ProgressEngine.get().claim(target, id));
    }

    /** Claims each currently available reward through the same idempotent single-reward transaction. */
    public static OperationResult claimAllRewardsResult(ServerPlayer player, String questId) {
        return claimAllRewardsResult(LEGACY_CONTEXT, player, questId);
    }

    public static OperationResult claimAllRewardsResult(OperationContext context, ServerPlayer player, String questId) {
        OperationResult readiness = validateWriteContext(context, player);
        if (readiness != null) return audited(context, player, "claim_all", questId, readiness);
        Optional<QuestDefinition> quest = definition(questId);
        if (quest.isEmpty()) return audited(context, player, "claim_all", questId, resolve(questId).isEmpty()
                ? invalidId("quest", questId)
                : OperationResult.rejected("NOT_FOUND", "Unknown quest " + questId));
        if (quest.orElseThrow().rewards().isEmpty()) {
            return audited(context, player, "claim_all", questId,
                    OperationResult.noChange("NO_REWARDS", "Quest has no rewards"));
        }

        int changed = 0;
        for (var reward : quest.orElseThrow().rewards()) {
            OperationResult result = ProgressEngine.get().claim(player, reward.id());
            if (!result.success()) {
                // Earlier rewards remain valid and ledgered; report the partial boundary explicitly.
                String message = "Claimed " + changed + " rewards before failure: " + result.message();
                return audited(context, player, "claim_all", questId,
                        OperationResult.rejected("PARTIAL_FAILURE", message));
            }
            if (result.changed()) changed++;
        }
        OperationResult result = changed == 0
                ? OperationResult.noChange("ALREADY_CLAIMED", "All rewards were already claimed")
                : OperationResult.success("Claimed " + changed + " rewards");
        return audited(context, player, "claim_all", questId, result);
    }

    public static OperationResult toggleTrackedResult(ServerPlayer player, String questId) {
        return toggleTrackedResult(selfContext(player), player, questId);
    }

    public static OperationResult toggleTrackedResult(OperationContext context, ServerPlayer player, String questId) {
        return execute(context, player, questId, "quest", (target, id) -> ProgressEngine.get().toggleTracked(target, id));
    }

    public static OperationResult resetQuestResult(OperationContext context, ServerPlayer player, String questId) {
        return execute(context, player, questId, "reset_quest", (target, id) -> {
            if (definition(questId).isEmpty()) return OperationResult.rejected("NOT_FOUND", "Unknown quest " + questId);
            ProgressEngine.get().reset(target, id);
            return OperationResult.success("Quest progress reset");
        });
    }

    public static boolean openQuestScreen(ServerPlayer player, String questId) {
        return openQuestScreenResult(player, questId).success();
    }

    public static OperationResult openQuestScreenResult(ServerPlayer player, String questId) {
        return openQuestScreenResult(selfContext(player), player, questId);
    }

    public static OperationResult openQuestScreenResult(OperationContext context, ServerPlayer player, String questId) {
        OperationResult readiness = validateWriteContext(context, player);
        if (readiness != null) return audited(context, player, "open_screen", questId, readiness);
        if (questId != null && !questId.isBlank()) {
            Optional<ResourceLocation> id = resolve(questId);
            if (id.isEmpty()) return audited(context, player, "open_screen", questId, invalidId("quest", questId));
            if (snapshot().map(value -> value.quests().containsKey(id.orElseThrow())).orElse(false) == false) {
                return audited(context, player, "open_screen", questId,
                        OperationResult.rejected("NOT_FOUND", "Unknown quest " + questId));
            }
        }
        BrnQuestNetwork.syncAll(player, false);
        BrnQuestNetwork.openScreen(player, questId == null ? "" : questId);
        return audited(context, player, "open_screen", questId, OperationResult.success("Quest screen opened"));
    }

    public static Optional<QuestBookView> getActiveBook() {
        return snapshot().map(ApiViews::book);
    }

    public static List<ChapterGroupView> getChapterGroups() {
        return snapshot().map(value -> value.book().chapterGroups().stream()
                .map(group -> ApiViews.chapterGroup(value, group)).toList()).orElseGet(List::of);
    }

    public static Optional<ChapterGroupView> getChapterGroup(String groupId) {
        Optional<ResourceLocation> id = resolve(groupId);
        if (id.isEmpty()) return Optional.empty();
        return snapshot().flatMap(value -> value.book().chapterGroups().stream()
                .filter(group -> group.id().equals(id.orElseThrow())).findFirst()
                .map(group -> ApiViews.chapterGroup(value, group)));
    }

    public static List<ChapterView> getChapters() {
        return snapshot().map(value -> value.book().chapters().stream().map(ApiViews::chapter).toList())
                .orElseGet(List::of);
    }

    public static Optional<ChapterView> getChapter(String chapterId) {
        Optional<ResourceLocation> id = resolve(chapterId);
        if (id.isEmpty()) return Optional.empty();
        return snapshot().flatMap(value -> value.book().chapters().stream()
                .filter(chapter -> chapter.id().equals(id.orElseThrow())).findFirst()).map(ApiViews::chapter);
    }

    public static List<QuestView> getQuests() {
        return snapshot().map(value -> value.book().quests().stream().map(ApiViews::quest).toList())
                .orElseGet(List::of);
    }

    public static Optional<QuestView> getQuest(String questId) {
        return definition(questId).map(ApiViews::quest);
    }

    public static Optional<TaskView> getTask(String taskId) {
        Optional<ResourceLocation> id = resolve(taskId);
        if (id.isEmpty()) return Optional.empty();
        return snapshot().stream().flatMap(value -> value.book().quests().stream())
                .flatMap(quest -> quest.tasks().stream()).filter(task -> task.id().equals(id.orElseThrow()))
                .findFirst().map(ApiViews::task);
    }

    public static Optional<RewardView> getReward(String rewardId) {
        Optional<ResourceLocation> id = resolve(rewardId);
        if (id.isEmpty()) return Optional.empty();
        return snapshot().stream().flatMap(value -> value.book().quests().stream())
                .flatMap(quest -> quest.rewards().stream()).filter(reward -> reward.id().equals(id.orElseThrow()))
                .findFirst().map(ApiViews::reward);
    }

    /** Progress storage is server-thread-only even though the returned projection is immutable. */
    public static Optional<ProgressView> getProgress(ServerPlayer player, String questId) {
        if (player == null || player.getServer() == null || !player.getServer().isSameThread()) return Optional.empty();
        Optional<QuestDefinition> quest = definition(questId);
        if (quest.isEmpty()) return Optional.empty();
        var progress = ProgressEngine.get().progress(player);
        QuestDefinition definition = quest.orElseThrow();
        Map<ResourceLocation, Long> taskProgress = definition.tasks().stream().collect(Collectors.toUnmodifiableMap(
                task -> task.id(), task -> progress.taskProgress(task.id().toString())));
        Set<ResourceLocation> claimedRewards = definition.rewards().stream()
                .filter(reward -> progress.isClaimed(reward.id().toString()))
                .map(reward -> reward.id()).collect(Collectors.toUnmodifiableSet());
        return Optional.of(new ProgressView(definition.bookId(), definition.id(),
                progress.status(definition.id().toString()), taskProgress, claimedRewards,
                progress.completedAt(definition.id().toString()), progress.revision()));
    }

    private static OperationResult execute(OperationContext context, ServerPlayer player, String rawId, String objectType,
                                           BiFunction<ServerPlayer, ResourceLocation, OperationResult> operation) {
        OperationResult readiness = validateWriteContext(context, player);
        if (readiness != null) return audited(context, player, objectType, rawId, readiness);
        Optional<ResourceLocation> id = resolve(rawId);
        if (id.isEmpty()) return audited(context, player, objectType, rawId, invalidId(objectType, rawId));
        return audited(context, player, objectType, rawId, operation.apply(player, id.orElseThrow()));
    }

    /** Returns null only when the caller may safely enter a server progress transaction. */
    private static OperationResult validateWriteContext(OperationContext context, ServerPlayer player) {
        if (context == null) return OperationResult.invalid("INVALID_CONTEXT", "Operation context must not be null");
        if (player == null) return OperationResult.invalid("INVALID_PLAYER", "Player must not be null");
        if (!context.mayModify(player)) {
            return OperationResult.forbidden("FORBIDDEN", "Actor may not modify the target player's quest state");
        }
        if (player.getServer() == null) return OperationResult.notReady("PLAYER_OFFLINE", "Player is not attached to a server");
        if (!player.getServer().isSameThread()) {
            return OperationResult.invalid("WRONG_THREAD", "BRNQuest write operations must run on the server thread");
        }
        if (snapshot().isEmpty()) return OperationResult.notReady("NO_BOOK", "No active quest book");
        return null;
    }

    private static OperationResult audited(OperationContext context, ServerPlayer target, String action,
                                           String objectId, OperationResult result) {
        String actor = context == null ? "<missing>" : context.actorId();
        String source = context == null ? "<missing>" : context.source();
        String targetId = target == null ? "<missing>" : target.getUUID().toString();
        BRNQuest.LOGGER.info("[BRNQuest/AUDIT] actor={} source={} action={} target={} object={} status={} code={} changed={}",
                actor, source, action, targetId, String.valueOf(objectId), result.status(), result.code(), result.changed());
        return result;
    }

    private static OperationResult invalidId(String objectType, String value) {
        return OperationResult.invalid("INVALID_ID", "Invalid " + objectType + " ID " + String.valueOf(value));
    }

    private static OperationContext selfContext(ServerPlayer player) {
        return player == null ? LEGACY_CONTEXT : OperationContext.self(player);
    }

    private static Optional<QuestDefinition> definition(String questId) {
        Optional<ResourceLocation> id = resolve(questId);
        if (id.isEmpty()) return Optional.empty();
        return snapshot().map(value -> value.quests().get(id.orElseThrow()));
    }

    private static Optional<QuestBookSnapshot> snapshot() {
        return QuestBookManager.get().active();
    }

    private static Optional<ResourceLocation> resolve(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        QuestBookSnapshot snapshot = snapshot().orElse(null);
        if (snapshot != null && snapshot.book().legacyIds().containsKey(value)) {
            return Optional.of(snapshot.book().legacyIds().get(value));
        }
        if (QuestIds.isLegacy(value)) {
            String namespace = snapshot == null ? "brnquest" : snapshot.book().id().getNamespace();
            return Optional.of(QuestIds.normalize(namespace, value));
        }
        return Optional.ofNullable(ResourceLocation.tryParse(value));
    }
}
