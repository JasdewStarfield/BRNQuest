package yourscraft.jasdewstarfield.brnquestexample;

import com.mojang.serialization.Codec;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigFieldDescriptor;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigValueType;
import yourscraft.jasdewstarfield.brnquest.reward.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Public-API-only example: adding a player tag is intrinsically idempotent.
 * This is not a durable receipt recipe for commands or other non-idempotent side effects.
 */
final class GuardedTagReward implements RewardType<Map<String, String>> {
    public Codec<Map<String, String>> configCodec() { return Codec.unboundedMap(Codec.STRING, Codec.STRING); }
    public List<ConfigFieldDescriptor> configFields() {
        return List.of(ConfigFieldDescriptor.field("tag", ConfigValueType.TEXT).asRequired()
                        .withDefault("brnquest_example_reward").withLabel("screen.brnquest_example.field.tag"),
                // Deliberately shares tokens with command rewards, but owns different display labels.
                ConfigFieldDescriptor.enumeration("source_mode", List.of("explicit", "player"))
                        .withDefault("explicit").withLabel("screen.brnquest_example.field.mode")
                        .withValueLabels(Map.of("explicit", "screen.brnquest_example.mode.explicit",
                                "player", "screen.brnquest_example.mode.player")));
    }
    public Map<String, String> normalizeConfig(Map<String, String> config) {
        var normalized = new java.util.TreeMap<>(config);
        normalized.computeIfPresent("tag", (key, value) -> value.strip());
        return normalized;
    }
    public Optional<RewardClaimHandler> claimHandler() {
        return Optional.of(context -> {
            var player = context.rewardContext().player();
            String tag = context.rewardContext().reward().config().getOrDefault("tag", "").strip();
            if (tag.isBlank() || player.getTags().contains("brnquest_example_block"))
                return RewardClaimResult.failure("EXAMPLE_BLOCKED", "External condition rejects this reward");
            if (player.getTags().contains("brnquest_example_wait"))
                return RewardClaimResult.pending("EXAMPLE_PENDING", "Wait for the external condition to clear");
            // A set insertion remains safe even if ordinary claim data is restored to an earlier state.
            if (!player.getTags().contains(tag) && !player.addTag(tag))
                return RewardClaimResult.failure("EXAMPLE_TAG_LIMIT", "Player tag limit reached");
            return RewardClaimResult.success("Example tag granted");
        });
    }
    public RewardResult execute(RewardContext context, Map<String, String> config) {
        return RewardResult.failure("Use the authoritative claim handler");
    }
}
