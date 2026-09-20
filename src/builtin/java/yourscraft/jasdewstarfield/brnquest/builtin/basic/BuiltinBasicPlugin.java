package yourscraft.jasdewstarfield.brnquest.builtin.basic;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigFieldDescriptor;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigValueType;
import yourscraft.jasdewstarfield.brnquest.extension.BrnQuestPlugin;
import yourscraft.jasdewstarfield.brnquest.extension.BrnQuestExtensionRegistrar;
import yourscraft.jasdewstarfield.brnquest.task.*;
import yourscraft.jasdewstarfield.brnquest.reward.*;
import java.util.List;
import java.util.Map;

/** Built-in foundation types use the same staged registration contract as companion mods. */
public final class BuiltinBasicPlugin implements BrnQuestPlugin {
    public ResourceLocation id() { return ResourceLocation.parse("brnquest:builtin_basic"); }
    public void register(BrnQuestExtensionRegistrar registrar) {
        // Preserve schema-1 IDs and fields so existing books need no conversion.
        registrar.task(TaskTypes.CHECKMARK, new CheckmarkTask())
                .task(TaskTypes.CUSTOM, new ProgressTask())
                .task(TaskTypes.XP, new ExperienceTask());
        registrar.reward(RewardTypes.XP, new ExperienceReward(false))
                .reward(RewardTypes.XP_LEVELS, new ExperienceReward(true))
                .reward(RewardTypes.CUSTOM, new CustomReward());
    }

    private static final class CheckmarkTask implements TaskType<Map<String, String>> {
        public Codec<Map<String, String>> configCodec() { return Codec.unboundedMap(Codec.STRING, Codec.STRING); }
        public List<ConfigFieldDescriptor> configFields() {
            return List.of(ConfigFieldDescriptor.field("title", ConfigValueType.TEXT)
                    .withHelp("Optional objective title"));
        }
        public boolean satisfied(TaskContext context, Map<String, String> config) {
            return context.progress() >= 1;
        }
        public boolean allowsManualSubmission(Map<String, String> config) { return true; }
        public boolean acceptsQuestCompletionIntent(Map<String, String> config) { return true; }
        public TaskSubmissionResult submit(TaskContext context, Map<String, String> config) {
            return TaskSubmissionResult.accepted();
        }
        public Component describe(yourscraft.jasdewstarfield.brnquest.api.TaskView task, Map<String, String> config) {
            return Component.literal(config.getOrDefault("title", task.typeId().toString()));
        }
    }

    private static final class ProgressTask implements TaskType<Map<String, String>> {
        public Codec<Map<String, String>> configCodec() { return Codec.unboundedMap(Codec.STRING, Codec.STRING); }
        public boolean satisfied(TaskContext context, Map<String, String> config) {
            return context.progress() >= 1;
        }
        public Component describe(yourscraft.jasdewstarfield.brnquest.api.TaskView task, Map<String, String> config) {
            return Component.literal(config.getOrDefault("title", task.typeId().toString()));
        }
    }

    private static final class ExperienceTask implements TaskType<Map<String, String>> {
        public Codec<Map<String, String>> configCodec() { return Codec.unboundedMap(Codec.STRING, Codec.STRING); }
        public List<ConfigFieldDescriptor> configFields() {
            return List.of(
                    ConfigFieldDescriptor.field("value", ConfigValueType.INTEGER).withDefault("1").withRange(1, Integer.MAX_VALUE)
                            .withHelp("Experience amount to submit"),
                    ConfigFieldDescriptor.field("points", ConfigValueType.BOOLEAN).withDefault("true")
                            .withHelp("Use raw experience points instead of whole levels"));
        }
        public boolean satisfied(TaskContext context, Map<String, String> config) { return context.progress() >= 1; }
        public boolean allowsManualSubmission(Map<String, String> config) { return true; }
        public TaskSubmissionResult submit(TaskContext context, Map<String, String> config) {
            int value;
            try { value = Integer.parseInt(config.getOrDefault("value", "1").replaceAll("[^0-9-]", "")); }
            catch (NumberFormatException exception) { return TaskSubmissionResult.failure("INVALID_XP", "Invalid experience amount"); }
            if (value < 1) return TaskSubmissionResult.failure("INVALID_XP", "Experience amount must be positive");
            boolean points = Boolean.parseBoolean(config.getOrDefault("points", "true").replace("1b", "true"));
            if (points && context.player().totalExperience < value) return TaskSubmissionResult.failure("INSUFFICIENT_XP", "Not enough experience points");
            if (!points && context.player().experienceLevel < value) return TaskSubmissionResult.failure("INSUFFICIENT_XP", "Not enough experience levels");
            if (points) context.player().giveExperiencePoints(-value); else context.player().giveExperienceLevels(-value);
            return TaskSubmissionResult.accepted();
        }
        public Component describe(yourscraft.jasdewstarfield.brnquest.api.TaskView task, Map<String, String> config) {
            boolean points = Boolean.parseBoolean(config.getOrDefault("points", "true").replace("1b", "true"));
            return Component.literal(config.getOrDefault("value", "1") + (points ? " experience points" : " levels"));
        }
    }

    private record ExperienceReward(boolean levels) implements RewardType<Map<String, String>> {
        public java.util.Optional<ComposableReward> composition() { return java.util.Optional.of(new BasicComposition(this, levels ? "xp_levels" : "xp")); }
        public Codec<Map<String, String>> configCodec() { return Codec.unboundedMap(Codec.STRING, Codec.STRING); }
        public List<ConfigFieldDescriptor> configFields() {
            return List.of(ConfigFieldDescriptor.field(levels ? "xp_levels" : "xp", ConfigValueType.INTEGER)
                    .withDefault("1").withRange(1, Integer.MAX_VALUE)
                    .withHelp(levels ? "Whole experience levels to grant" : "Raw experience points to grant"));
        }
        public RewardResult execute(RewardContext context, Map<String, String> config) {
            String key = levels ? "xp_levels" : "xp";
            try {
                int amount = Integer.parseInt(config.getOrDefault(key, "1").replaceAll("[^0-9-]", ""));
                if (amount < 1) return RewardResult.failure("Experience reward must be positive");
                if (levels) context.player().giveExperienceLevels(amount); else context.player().giveExperiencePoints(amount);
                return RewardResult.success("Granted " + amount + (levels ? " experience levels" : " experience points"));
            } catch (NumberFormatException exception) {
                return RewardResult.failure("Invalid experience reward");
            }
        }
    }

    /** Custom rewards acknowledge an external workflow without running a script. */
    private static final class CustomReward implements RewardType<Map<String, String>> {
        public Codec<Map<String, String>> configCodec() { return Codec.unboundedMap(Codec.STRING, Codec.STRING); }
        public java.util.Optional<ComposableReward> composition() { return java.util.Optional.of(new BasicComposition(this, "custom")); }
        public RewardResult execute(RewardContext context, Map<String, String> config) {
            return RewardResult.success("Custom reward acknowledged");
        }
    }

}
