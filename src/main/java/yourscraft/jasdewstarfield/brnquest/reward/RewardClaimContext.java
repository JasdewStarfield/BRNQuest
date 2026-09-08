package yourscraft.jasdewstarfield.brnquest.reward;

import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerId;
import java.util.Objects;

/** Immutable claim identity; no mutable progress ledger is exposed to the extension. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public record RewardClaimContext(RewardContext rewardContext, ProgressOwnerId ownerId, int completionCycle, String claimGeneration) {
    public RewardClaimContext {
        Objects.requireNonNull(rewardContext, "rewardContext");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(claimGeneration, "claimGeneration");
    }
    /** Compatibility constructor for consumers without an explicit reset generation. */
    public RewardClaimContext(RewardContext rewardContext, ProgressOwnerId ownerId, int completionCycle) {
        this(rewardContext, ownerId, completionCycle, "");
    }
}
