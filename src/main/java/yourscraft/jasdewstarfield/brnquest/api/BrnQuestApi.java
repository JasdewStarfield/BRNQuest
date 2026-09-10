package yourscraft.jasdewstarfield.brnquest.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.BRNQuest;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestIds;
import yourscraft.jasdewstarfield.brnquest.network.BrnQuestNetwork;
import yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerService;
import yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerLifecycle;
import yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerView;
import yourscraft.jasdewstarfield.brnquest.progress.ProgressEngine;
import yourscraft.jasdewstarfield.brnquest.task.TaskSubmissionSelection;
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
        return completeTaskResult(context, player, questId, taskId, TaskSubmissionSelection.AUTOMATIC);
    }

    /** Context-aware submission with optional child item entries selected by the player. */
    @ApiStatus(ApiStability.INTERNAL)
    public static OperationResult completeTaskResult(OperationContext context, ServerPlayer player,
                                                     String questId, String taskId,
                                                     TaskSubmissionSelection selection) {
        OperationResult readiness = validateWriteContext(context, player);
        if (readiness != null) return audited(context, player, "complete_task", taskId, readiness);
        Optional<ResourceLocation> resolvedQuest = resolve(questId);
        Optional<ResourceLocation> resolvedTask = resolve(taskId);
        if (resolvedQuest.isEmpty()) return audited(context, player, "complete_task", questId, invalidId("quest", questId));
        if (resolvedTask.isEmpty()) return audited(context, player, "complete_task", taskId, invalidId("task", taskId));
        return audited(context, player, "complete_task", taskId,
                ProgressEngine.get().completeTask(player, resolvedQuest.orElseThrow(), resolvedTask.orElseThrow(),
                        selection));
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

        int changed = 0, interactive = 0;
        for (var reward : quest.orElseThrow().rewards()) {
            var type = yourscraft.jasdewstarfield.brnquest.reward.RewardTypeRegistry.get(reward.typeId());
            if (type != null && type.requiresManualClaim(reward.config())) { interactive++; continue; }
            OperationResult result = ProgressEngine.get().claim(player, reward.id());
            if (!result.success()) {
                // Earlier rewards remain valid and ledgered; report the partial boundary explicitly.
                String message = "Claimed " + changed + " rewards before failure: " + result.message();
                return audited(context, player, "claim_all", questId,
                        OperationResult.rejected("PARTIAL_FAILURE", message));
            }
            if (result.changed()) changed++;
        }
        OperationResult result = interactive > 0
                ? (changed == 0 ? OperationResult.noChange("MANUAL_SELECTION_REQUIRED", "Skipped " + interactive + " interactive rewards; claim them individually")
                : OperationResult.success("Claimed " + changed + " rewards; skipped " + interactive + " interactive rewards; claim them individually"))
                : changed == 0
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

    /** Resolves author text for the caller's locale without altering the active book. */
    public static Optional<QuestBookView> getActiveBook(String locale) {
        return snapshot().map(value -> ApiViews.book(value, locale));
    }

    public static List<ChapterGroupView> getChapterGroups(String locale) {
        return snapshot().map(value -> value.book().chapterGroups().stream()
                .map(group -> ApiViews.chapterGroup(value, group, locale)).toList()).orElseGet(List::of);
    }

    public static Optional<ChapterGroupView> getChapterGroup(String groupId, String locale) {
        Optional<ResourceLocation> id = resolve(groupId);
        if (id.isEmpty()) return Optional.empty();
        return snapshot().flatMap(value -> value.book().chapterGroups().stream()
                .filter(group -> group.id().equals(id.orElseThrow())).findFirst()
                .map(group -> ApiViews.chapterGroup(value, group, locale)));
    }

    public static List<ChapterView> getChapters(String locale) {
        return snapshot().map(value -> value.book().chapters().stream()
                .map(chapter -> ApiViews.chapter(value, chapter, locale)).toList()).orElseGet(List::of);
    }

    public static Optional<ChapterView> getChapter(String chapterId, String locale) {
        Optional<ResourceLocation> id = resolve(chapterId);
        if (id.isEmpty()) return Optional.empty();
        return snapshot().flatMap(value -> value.book().chapters().stream()
                .filter(chapter -> chapter.id().equals(id.orElseThrow())).findFirst()
                .map(chapter -> ApiViews.chapter(value, chapter, locale)));
    }

    public static List<QuestView> getQuests(String locale) {
        return snapshot().map(value -> value.book().quests().stream()
                .map(quest -> ApiViews.quest(value, quest, locale)).toList()).orElseGet(List::of);
    }

    /** Returns immutable source translations for integrations that provide their own locale selection. */
    public static Map<String, Map<String, String>> getTranslations() {
        return snapshot().map(value -> value.book().localization().translations()).orElseGet(Map::of);
    }

    /** Resolves a semantic text key with configured fallback-locale behavior. */
    public static String resolveText(String locale, String key, String fallback) {
        return snapshot().map(value -> value.book().localization().resolve(locale, key, fallback)).orElse(fallback);
    }

    /** Returns a quest view whose three author-facing text fields are resolved for the requested locale. */
    public static Optional<QuestView> getQuest(String questId, String locale) {
        Optional<ResourceLocation> id = resolve(questId);
        if (id.isEmpty()) return Optional.empty();
        return snapshot().flatMap(value -> Optional.ofNullable(value.quests().get(id.orElseThrow()))
                .map(quest -> ApiViews.quest(value, quest, locale)));
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

    /** Returns the current immutable owner without exposing the mutable progress store. */
    public static Optional<ProgressOwnerView> getProgressOwner(ServerPlayer player) {
        return ProgressOwnerService.resolve(player);
    }

    /** Progress storage is server-thread-only even though the returned projection is immutable. */
    public static Optional<ProgressView> getProgress(ServerPlayer player, String questId) {
        if (player == null || player.getServer() == null || !player.getServer().isSameThread()) return Optional.empty();
        Optional<QuestDefinition> quest = definition(questId);
        if (quest.isEmpty()) return Optional.empty();
        Optional<ProgressOwnerView> owner = ProgressOwnerService.resolve(player)
                .filter(value -> value.lifecycle() == ProgressOwnerLifecycle.ACTIVE);
        if (owner.isEmpty()) return Optional.empty();
        var progress = ProgressEngine.get().progress(player);
        QuestDefinition definition = quest.orElseThrow();
        Map<ResourceLocation, Long> taskProgress = definition.tasks().stream().collect(Collectors.toUnmodifiableMap(
                task -> task.id(), task -> progress.taskProgress(task.id().toString())));
        Set<ResourceLocation> claimedRewards = definition.rewards().stream()
                .filter(reward -> ProgressEngine.get().rewardClaimed(player, reward))
                .map(reward -> reward.id()).collect(Collectors.toUnmodifiableSet());
        return Optional.of(new ProgressView(owner.orElseThrow().id(), definition.bookId(), definition.id(),
                ProgressEngine.get().visibleStatuses(player).getOrDefault(definition.id().toString(), QuestStatus.LOCKED), taskProgress, claimedRewards,
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
        BRNQuest.LOGGER.debug("[BRNQuest/AUDIT] actor={} source={} action={} target={} object={} status={} code={} changed={}",
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
        if (snapshot != null && snapshot.book().legacyIds().containsKey(value)
                && snapshot.quests().containsKey(snapshot.book().legacyIds().get(value))) {
            return Optional.of(snapshot.book().legacyIds().get(value));
        }
        if (QuestIds.isLegacy(value)) {
            String namespace = snapshot == null ? "brnquest" : snapshot.book().id().getNamespace();
            return Optional.of(QuestIds.normalize(namespace, value));
        }
        return Optional.ofNullable(ResourceLocation.tryParse(value));
    }
}
