package yourscraft.jasdewstarfield.brnquest.reward;

import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;

/** Invoked on the server thread under the owner lock, after eligibility checks and before claiming.
 * Implementations must prevent their own replays, including pending/failed attempts and crashes.
 * Only SUCCESS commits the normal receipt; PENDING and FAILURE leave it unresolved.
 */
@ApiStatus(ApiStability.EXPERIMENTAL)
@FunctionalInterface
public interface RewardClaimHandler {
    RewardClaimResult claim(RewardClaimContext context);
}
