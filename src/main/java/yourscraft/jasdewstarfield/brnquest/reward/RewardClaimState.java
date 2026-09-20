package yourscraft.jasdewstarfield.brnquest.reward;

import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import java.util.Objects;

/** Read-only current identity for type-owned recovery; eligibility is rechecked by every claim transaction. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public record RewardClaimState(RewardClaimContext context, String revision, boolean eligible) {
    public RewardClaimState { Objects.requireNonNull(context); Objects.requireNonNull(revision); }
}
