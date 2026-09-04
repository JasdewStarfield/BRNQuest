package yourscraft.jasdewstarfield.brnquest.compat.kubejs;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.api.BrnQuestApi;
import yourscraft.jasdewstarfield.brnquest.api.OperationContext;
import yourscraft.jasdewstarfield.brnquest.api.OperationResult;
import yourscraft.jasdewstarfield.brnquest.api.ProgressView;
import yourscraft.jasdewstarfield.brnquest.api.QuestBehaviorView;
import yourscraft.jasdewstarfield.brnquest.api.QuestBookView;
import yourscraft.jasdewstarfield.brnquest.api.QuestView;
import yourscraft.jasdewstarfield.brnquest.api.RewardView;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;
import yourscraft.jasdewstarfield.brnquest.runtime.ScriptExtensionRegistry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Server-script facade that exposes only immutable scalar, list, and map projections. */
public final class BrnQuestKubeJSBindings {
    public static final BrnQuestKubeJSBindings INSTANCE = new BrnQuestKubeJSBindings();
    private static final OperationContext CONTEXT = OperationContext.integration(
            ResourceLocation.fromNamespaceAndPath("brnquest", "kubejs"));

    private BrnQuestKubeJSBindings() {}

    /** Declares an externally progressed task type only during server-script evaluation. */
    public void registerTaskType(String typeId) {
        ResourceLocation id = requireId(typeId, "task type");
        ScriptExtensionRegistry.stageTask(id, BrnQuestKubeJSScriptTypes.task());
    }

    /** Declares a reward type whose side effect is delivered through BRNQuestEvents.customReward. */
    public void registerRewardType(String typeId) {
        ResourceLocation id = requireId(typeId, "reward type");
        ScriptExtensionRegistry.stageReward(id, BrnQuestKubeJSScriptTypes.reward(id));
    }

    public Map<String, Object> getScriptExtensions() {
        var snapshot = ScriptExtensionRegistry.snapshot();
        LinkedHashMap<String, Object> output = new LinkedHashMap<>();
        output.put("taskTypeIds", snapshot.taskTypeIds());
        output.put("rewardTypeIds", snapshot.rewardTypeIds());
        output.put("registrationOpen", snapshot.registrationOpen());
        return immutable(output);
    }

    public Map<String, Object> getActiveBook() {
        return BrnQuestApi.getActiveBook().map(BrnQuestKubeJSBindings::book).orElse(null);
    }

    public Map<String, Object> getQuest(String questId) {
        return BrnQuestApi.getQuest(questId).map(BrnQuestKubeJSBindings::quest).orElse(null);
    }

    public Map<String, Object> getQuest(String questId, String locale) {
        return BrnQuestApi.getQuest(questId, locale).map(BrnQuestKubeJSBindings::quest).orElse(null);
    }

    public Map<String, Object> getProgress(ServerPlayer player, String questId) {
        return BrnQuestApi.getProgress(player, questId).map(BrnQuestKubeJSBindings::progress).orElse(null);
    }

    public boolean isQuestCompleted(ServerPlayer player, String questId) {
        return BrnQuestApi.isQuestCompleted(player, questId);
    }

    /** Integration completion deliberately bypasses ordinary objective requirements. */
    public Map<String, Object> completeQuest(ServerPlayer player, String questId) {
        return result(BrnQuestApi.completeQuestResult(CONTEXT, player, questId));
    }

    public Map<String, Object> submitQuest(ServerPlayer player, String questId) {
        return submitQuest(player, questId, false);
    }

    /** Normal completion preserves dependency, task, and optional checkmark semantics. */
    public Map<String, Object> submitQuest(ServerPlayer player, String questId, boolean checkmarkIntent) {
        return result(BrnQuestApi.submitQuestCompletionResult(CONTEXT, player, questId, checkmarkIntent));
    }

    public Map<String, Object> completeTask(ServerPlayer player, String questId, String taskId) {
        return result(BrnQuestApi.completeTaskResult(CONTEXT, player, questId, taskId));
    }

    public Map<String, Object> addTaskProgress(ServerPlayer player, String taskId, long amount) {
        return result(BrnQuestApi.addTaskProgressResult(CONTEXT, player, taskId, amount));
    }

    public Map<String, Object> claimReward(ServerPlayer player, String rewardId) {
        return result(BrnQuestApi.claimRewardResult(CONTEXT, player, rewardId));
    }

    public Map<String, Object> claimAllRewards(ServerPlayer player, String questId) {
        return result(BrnQuestApi.claimAllRewardsResult(CONTEXT, player, questId));
    }

    public Map<String, Object> toggleTracked(ServerPlayer player, String questId) {
        return result(BrnQuestApi.toggleTrackedResult(CONTEXT, player, questId));
    }

    public Map<String, Object> openQuest(ServerPlayer player) {
        return openQuest(player, "");
    }

    /** BrnQuestApi synchronizes the authoritative snapshot before sending the open-screen payload. */
    public Map<String, Object> openQuest(ServerPlayer player, String questId) {
        return result(BrnQuestApi.openQuestScreenResult(CONTEXT, player, questId));
    }

    private static Map<String, Object> result(OperationResult value) {
        LinkedHashMap<String, Object> output = new LinkedHashMap<>();
        output.put("status", value.status().name());
        output.put("code", value.code());
        output.put("message", value.message());
        output.put("success", value.success());
        output.put("changed", value.changed());
        return immutable(output);
    }

    private static Map<String, Object> book(QuestBookView value) {
        LinkedHashMap<String, Object> output = new LinkedHashMap<>();
        output.put("id", value.id().toString());
        output.put("schemaVersion", value.schemaVersion());
        output.put("title", value.title());
        output.put("revision", value.revision());
        output.put("chapterGroupIds", ids(value.chapterGroupIds()));
        output.put("chapterIds", ids(value.chapterIds()));
        output.put("questIds", ids(value.questIds()));
        LinkedHashMap<String, Object> aliases = new LinkedHashMap<>();
        new TreeMap<>(value.legacyIds()).forEach((alias, id) -> aliases.put(alias, id.toString()));
        output.put("legacyIds", immutable(aliases));
        return immutable(output);
    }

    static Map<String, Object> quest(QuestView value) {
        LinkedHashMap<String, Object> output = new LinkedHashMap<>();
        output.put("bookId", value.bookId().toString());
        output.put("id", value.id().toString());
        output.put("chapterId", value.chapterId().toString());
        output.put("title", value.title());
        output.put("subtitle", value.subtitle());
        output.put("description", value.description());
        output.put("icon", value.icon());
        output.put("x", value.x());
        output.put("y", value.y());
        output.put("dependencies", ids(value.dependencies()));
        output.put("tasks", value.tasks().stream().map(BrnQuestKubeJSBindings::task).toList());
        output.put("rewards", value.rewards().stream().map(BrnQuestKubeJSBindings::reward).toList());
        output.put("legacyId", value.legacyId());
        output.put("behavior", behavior(value.behavior()));
        return immutable(output);
    }

    static Map<String, Object> task(TaskView value) {
        LinkedHashMap<String, Object> output = new LinkedHashMap<>();
        output.put("bookId", value.bookId().toString());
        output.put("id", value.id().toString());
        output.put("typeId", value.typeId().toString());
        output.put("config", immutable(new LinkedHashMap<>(new TreeMap<>(value.config()))));
        output.put("optional", value.optional());
        return immutable(output);
    }

    static Map<String, Object> reward(RewardView value) {
        LinkedHashMap<String, Object> output = new LinkedHashMap<>();
        output.put("bookId", value.bookId().toString());
        output.put("id", value.id().toString());
        output.put("typeId", value.typeId().toString());
        output.put("config", immutable(new LinkedHashMap<>(new TreeMap<>(value.config()))));
        output.put("claimPolicy", value.claimPolicy());
        output.put("teamReward", value.teamReward());
        return immutable(output);
    }

    static Map<String, Object> progress(ProgressView value) {
        LinkedHashMap<String, Object> output = new LinkedHashMap<>();
        output.put("ownerProviderId", value.owner().providerId().toString());
        output.put("ownerId", value.owner().ownerId().toString());
        output.put("bookId", value.bookId().toString());
        output.put("questId", value.questId().toString());
        output.put("status", value.status().name());
        LinkedHashMap<String, Object> taskProgress = new LinkedHashMap<>();
        value.taskProgress().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> taskProgress.put(entry.getKey().toString(), entry.getValue()));
        output.put("taskProgress", immutable(taskProgress));
        output.put("claimedRewardIds", value.claimedRewards().stream().map(ResourceLocation::toString).sorted().toList());
        output.put("completedAtEpochMillis", value.completedAtEpochMillis());
        output.put("revision", value.revision());
        return immutable(output);
    }

    private static Map<String, Object> behavior(QuestBehaviorView value) {
        LinkedHashMap<String, Object> output = new LinkedHashMap<>();
        output.put("hideUntilDependenciesVisible", value.hideUntilDependenciesVisible());
        output.put("hideUntilDependenciesComplete", value.hideUntilDependenciesComplete());
        output.put("invisibleUntilComplete", value.invisibleUntilComplete());
        output.put("visibleAfterTasks", value.visibleAfterTasks());
        output.put("hideDetailsUntilStartable", value.hideDetailsUntilStartable());
        output.put("hideTextUntilComplete", value.hideTextUntilComplete());
        output.put("hideLockIcon", value.hideLockIcon());
        output.put("dependencyRequirement", value.dependencyRequirement());
        output.put("minimumRequiredDependencies", value.minimumRequiredDependencies());
        output.put("sequentialTasks", value.sequentialTasks());
        output.put("repeatable", value.repeatable());
        output.put("repeatCooldownSeconds", value.repeatCooldownSeconds());
        output.put("ignoreRewardBlocking", value.ignoreRewardBlocking());
        return immutable(output);
    }

    private static List<String> ids(List<ResourceLocation> values) {
        return values.stream().map(ResourceLocation::toString).toList();
    }

    private static <V> Map<String, V> immutable(LinkedHashMap<String, V> values) {
        return Collections.unmodifiableMap(values);
    }

    private static ResourceLocation requireId(String value, String kind) {
        ResourceLocation id = value == null ? null : ResourceLocation.tryParse(value);
        if (id == null) throw new IllegalArgumentException("Invalid " + kind + " ID: " + value);
        return id;
    }
}
