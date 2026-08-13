package yourscraft.jasdewstarfield.brnquest.reward;

import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;

/** Structured result returned by a reward implementation. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public record RewardResult(boolean success, String message) {
    public static RewardResult success(String message) { return new RewardResult(true, message); }
    public static RewardResult failure(String message) { return new RewardResult(false, message); }
}
