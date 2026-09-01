package yourscraft.jasdewstarfield.brnquest.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/** Immutable typed reward configuration retained even when its type is unavailable. */
public record RewardDefinition(ResourceLocation bookId, ResourceLocation id, ResourceLocation typeId,
                               Map<String, String> config, String claimPolicy, boolean teamReward) {
    public static final Codec<RewardDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("book_id").forGetter(RewardDefinition::bookId),
            ResourceLocation.CODEC.fieldOf("id").forGetter(RewardDefinition::id),
            ResourceLocation.CODEC.fieldOf("type").forGetter(RewardDefinition::typeId),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("config", Map.of()).forGetter(RewardDefinition::config),
            Codec.STRING.optionalFieldOf("claim_policy", "manual").forGetter(RewardDefinition::claimPolicy),
            Codec.BOOL.optionalFieldOf("team_reward", false).forGetter(RewardDefinition::teamReward)
    ).apply(i, RewardDefinition::new));

    public RewardDefinition {
        config = Map.copyOf(config);
        claimPolicy = claimPolicy == null || claimPolicy.isBlank() ? "manual" : claimPolicy;
    }

    public RewardClaimPolicy policy() { return RewardClaimPolicy.parse(claimPolicy); }
}
