package yourscraft.jasdewstarfield.brnquest.compat.kubejs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.api.BrnQuestApi;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigFieldDescriptor;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigValueType;
import yourscraft.jasdewstarfield.brnquest.reward.RewardContext;
import yourscraft.jasdewstarfield.brnquest.reward.RewardResult;
import yourscraft.jasdewstarfield.brnquest.reward.RewardType;
import yourscraft.jasdewstarfield.brnquest.task.TaskContext;
import yourscraft.jasdewstarfield.brnquest.task.TaskType;

import java.util.List;
import java.util.Map;

/** Factory for deliberately narrow task/reward implementations backed by server scripts. */
final class BrnQuestKubeJSScriptTypes {
    private static final Codec<Map<String, String>> TASK_CONFIG = Codec.unboundedMap(Codec.STRING, Codec.STRING)
            .flatXmap(BrnQuestKubeJSScriptTypes::validateTaskConfig, BrnQuestKubeJSScriptTypes::validateTaskConfig);
    private static final Codec<Map<String, String>> REWARD_CONFIG = Codec.unboundedMap(Codec.STRING, Codec.STRING);

    private BrnQuestKubeJSScriptTypes() {}

    static TaskType<Map<String, String>> task() {
        return new ScriptProgressTask();
    }

    static RewardType<Map<String, String>> reward(ResourceLocation typeId) {
        return new ScriptReward(typeId);
    }

    private static DataResult<Map<String, String>> validateTaskConfig(Map<String, String> config) {
        try {
            return requiredProgress(config) > 0
                    ? DataResult.success(config)
                    : DataResult.error(() -> "required_progress must be positive");
        } catch (NumberFormatException exception) {
            return DataResult.error(() -> "required_progress must be an integer");
        }
    }

    private static long requiredProgress(Map<String, String> config) {
        return Long.parseLong(config.getOrDefault("required_progress", "1").replaceAll("[^0-9-]", ""));
    }

    /** Script tasks are progressed explicitly through BRNQuest.addTaskProgress(). */
    private static final class ScriptProgressTask implements TaskType<Map<String, String>> {
        @Override
        public Codec<Map<String, String>> configCodec() { return TASK_CONFIG; }

        @Override
        public boolean satisfied(TaskContext context, Map<String, String> config) {
            return context.progress() >= requiredProgress(config);
        }

        @Override
        public List<ConfigFieldDescriptor> configFields() {
            return List.of(
                    ConfigFieldDescriptor.field("title", ConfigValueType.TEXT)
                            .withHelp("Optional script objective title"),
                    ConfigFieldDescriptor.field("required_progress", ConfigValueType.INTEGER).withDefault("1")
                            .withRange(1, Long.MAX_VALUE).withHelp("Progress required from server scripts")
            );
        }

        @Override
        public Component describe(yourscraft.jasdewstarfield.brnquest.api.TaskView task,
                                  Map<String, String> config) {
            String title = config.getOrDefault("title", "");
            return Component.literal(title.isBlank() ? task.typeId().toString() : title);
        }
    }

    /** Script rewards delegate side effects to a targeted KubeJS event. */
    private record ScriptReward(ResourceLocation typeId) implements RewardType<Map<String, String>> {
        @Override
        public Codec<Map<String, String>> configCodec() { return REWARD_CONFIG; }

        @Override
        public List<ConfigFieldDescriptor> configFields() {
            return List.of(ConfigFieldDescriptor.field("title", ConfigValueType.TEXT)
                    .withHelp("Optional script reward title"));
        }

        @Override
        public RewardResult execute(RewardContext context, Map<String, String> config) {
            if (!BrnQuestKubeJSEvents.CUSTOM_REWARD.hasListeners(typeId)) {
                return RewardResult.failure("No KubeJS customReward listener is registered for " + typeId);
            }
            var progress = BrnQuestApi.getProgress(context.player(), context.questId().toString()).orElse(null);
            if (progress == null) return RewardResult.failure("Quest progress is unavailable");
            // This key is derived only from persisted owner, reward, and completion-cycle data.
            String key = progress.owner().providerId() + "|" + progress.owner().ownerId() + "|"
                    + context.reward().id() + "|" + progress.completedAtEpochMillis();
            // Ordinary shared rewards need a recipient dimension; retain legacy personal/team keys.
            if (!context.reward().teamReward() && !progress.owner().providerId().equals(
                    yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerProviders.PERSONAL)) {
                key += "|" + context.player().getUUID();
            }
            BrnQuestKubeJSEvents.CUSTOM_REWARD.post(ScriptType.SERVER, typeId,
                    new ScriptRewardKubeEvent(context.player(), context, key));
            return RewardResult.success("KubeJS custom reward dispatched");
        }
    }
}
