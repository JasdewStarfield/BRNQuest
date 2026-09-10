package yourscraft.jasdewstarfield.brnquest.reward;

import com.mojang.serialization.Codec;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigFieldDescriptor;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigValueType;
import java.util.List;

/** Commands must be prepared and journaled by the authoritative claim coordinator before execution. */
public final class CommandReward implements RewardType<CommandRewardConfig> {
    public java.util.Optional<ComposableReward> composition() { return java.util.Optional.of(new BuiltinComposition("command")); }
    public Codec<CommandRewardConfig> configCodec() { return CommandRewardConfig.CODEC; }
    public List<ConfigFieldDescriptor> configFields() {
        return List.of(ConfigFieldDescriptor.field("title", ConfigValueType.TEXT).withLabel("screen.brnquest.editor.config.title"),
                ConfigFieldDescriptor.field("command", ConfigValueType.TEXT).withLabel("screen.brnquest.editor.config.command").asRequired(),
                ConfigFieldDescriptor.enumeration("source_mode", List.of("explicit", "player")).withLabel("screen.brnquest.editor.config.source_mode")
                        .withValueLabels(java.util.Map.of("explicit", "screen.brnquest.command.source.explicit", "player", "screen.brnquest.command.source.player")).withDefault("explicit"),
                ConfigFieldDescriptor.field("permission_level", ConfigValueType.INTEGER).withLabel("screen.brnquest.editor.config.permission_level").withDefault("2").withRange(0, 4),
                ConfigFieldDescriptor.field("silent", ConfigValueType.BOOLEAN).withLabel("screen.brnquest.editor.config.silent").withDefault("false"),
                ConfigFieldDescriptor.field("feedback", ConfigValueType.TEXT).withLabel("screen.brnquest.editor.config.feedback"));
    }
    public java.util.Optional<RewardClaimHandler> claimHandler() { return java.util.Optional.of(CommandRewardService::claim); }
    public java.util.Map<String, String> normalizeConfig(java.util.Map<String, String> config) {
        var normalized = new java.util.TreeMap<>(config);
        normalized.computeIfPresent("command", (key, value) -> CommandRewardConfig.normalize(value));
        return normalized;
    }
    public RewardResult execute(RewardContext context, CommandRewardConfig config) {
        // Never expose an unjournaled execution route to extensions calling the generic executor.
        return RewardResult.failure("Command rewards require the authoritative claim coordinator");
    }
}
