package yourscraft.jasdewstarfield.brnquest.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
    private BrnQuestApi() {}

    public static boolean completeQuest(ServerPlayer player, String questId) {
        return completeQuestResult(player, questId).success();
    }

    public static OperationResult completeQuestResult(ServerPlayer player, String questId) {
        return execute(player, questId, "quest", (target, id) -> ProgressEngine.get().forceComplete(target, id));
    }

    public static OperationResult completeTaskResult(ServerPlayer player, String questId, String taskId) {
        OperationResult readiness = validateWriteContext(player);
        if (readiness != null) return readiness;
        Optional<ResourceLocation> resolvedQuest = resolve(questId);
        Optional<ResourceLocation> resolvedTask = resolve(taskId);
        if (resolvedQuest.isEmpty()) return invalidId("quest", questId);
        if (resolvedTask.isEmpty()) return invalidId("task", taskId);
        return ProgressEngine.get().completeTask(player, resolvedQuest.orElseThrow(), resolvedTask.orElseThrow());
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
        if (amount <= 0) return OperationResult.invalid("INVALID_AMOUNT", "Amount must be positive");
        return execute(player, taskId, "task", (target, id) -> ProgressEngine.get().addTaskProgress(target, id, amount));
    }

    public static boolean claimReward(ServerPlayer player, String rewardId) {
        return claimRewardResult(player, rewardId).success();
    }

    public static OperationResult claimRewardResult(ServerPlayer player, String rewardId) {
        return execute(player, rewardId, "reward", (target, id) -> ProgressEngine.get().claim(target, id));
    }

    /** Claims each currently available reward through the same idempotent single-reward transaction. */
    public static OperationResult claimAllRewardsResult(ServerPlayer player, String questId) {
        OperationResult readiness = validateWriteContext(player);
        if (readiness != null) return readiness;
        Optional<QuestDefinition> quest = definition(questId);
        if (quest.isEmpty()) return resolve(questId).isEmpty()
                ? invalidId("quest", questId)
                : OperationResult.rejected("NOT_FOUND", "Unknown quest " + questId);
        if (quest.orElseThrow().rewards().isEmpty()) {
            return OperationResult.noChange("NO_REWARDS", "Quest has no rewards");
        }

        int changed = 0;
        for (var reward : quest.orElseThrow().rewards()) {
            OperationResult result = ProgressEngine.get().claim(player, reward.id());
            if (!result.success()) {
                // Earlier rewards remain valid and ledgered; report the partial boundary explicitly.
                String message = "Claimed " + changed + " rewards before failure: " + result.message();
                return OperationResult.rejected("PARTIAL_FAILURE", message);
            }
            if (result.changed()) changed++;
        }
        return changed == 0
                ? OperationResult.noChange("ALREADY_CLAIMED", "All rewards were already claimed")
                : OperationResult.success("Claimed " + changed + " rewards");
    }

    public static OperationResult toggleTrackedResult(ServerPlayer player, String questId) {
        return execute(player, questId, "quest", (target, id) -> ProgressEngine.get().toggleTracked(target, id));
    }

    public static boolean openQuestScreen(ServerPlayer player, String questId) {
        return openQuestScreenResult(player, questId).success();
    }

    public static OperationResult openQuestScreenResult(ServerPlayer player, String questId) {
        OperationResult readiness = validateWriteContext(player);
        if (readiness != null) return readiness;
        if (questId != null && !questId.isBlank()) {
            Optional<ResourceLocation> id = resolve(questId);
            if (id.isEmpty()) return invalidId("quest", questId);
            if (snapshot().map(value -> value.quests().containsKey(id.orElseThrow())).orElse(false) == false) {
                return OperationResult.rejected("NOT_FOUND", "Unknown quest " + questId);
            }
        }
        BrnQuestNetwork.syncAll(player, false);
        BrnQuestNetwork.openScreen(player, questId == null ? "" : questId);
        return OperationResult.success("Quest screen opened");
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

    private static OperationResult execute(ServerPlayer player, String rawId, String objectType,
                                           BiFunction<ServerPlayer, ResourceLocation, OperationResult> operation) {
        OperationResult readiness = validateWriteContext(player);
        if (readiness != null) return readiness;
        Optional<ResourceLocation> id = resolve(rawId);
        if (id.isEmpty()) return invalidId(objectType, rawId);
        return operation.apply(player, id.orElseThrow());
    }

    /** Returns null only when the caller may safely enter a server progress transaction. */
    private static OperationResult validateWriteContext(ServerPlayer player) {
        if (player == null) return OperationResult.invalid("INVALID_PLAYER", "Player must not be null");
        if (player.getServer() == null) return OperationResult.notReady("PLAYER_OFFLINE", "Player is not attached to a server");
        if (!player.getServer().isSameThread()) {
            return OperationResult.invalid("WRONG_THREAD", "BRNQuest write operations must run on the server thread");
        }
        if (snapshot().isEmpty()) return OperationResult.notReady("NO_BOOK", "No active quest book");
        return null;
    }

    private static OperationResult invalidId(String objectType, String value) {
        return OperationResult.invalid("INVALID_ID", "Invalid " + objectType + " ID " + String.valueOf(value));
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
