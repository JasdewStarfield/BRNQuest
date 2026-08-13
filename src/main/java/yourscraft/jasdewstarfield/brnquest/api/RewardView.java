package yourscraft.jasdewstarfield.brnquest.api;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/** Immutable public reward definition, including its idempotent claim policy. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public record RewardView(ResourceLocation bookId, ResourceLocation id, ResourceLocation typeId,
                         Map<String, String> config, String claimPolicy, boolean teamReward) {
    public RewardView {
        config = Map.copyOf(config);
    }
}
