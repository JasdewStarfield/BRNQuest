package yourscraft.jasdewstarfield.brnquest.reward;

import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import java.util.Objects;

/** Typed outcome: coordination must never infer pending state from human-readable messages. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public record RewardClaimResult(State state, String code, String message) {
    public enum State { SUCCESS, PENDING, FAILURE }
    public RewardClaimResult {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }
    public static RewardClaimResult success(String message) { return new RewardClaimResult(State.SUCCESS, "OK", message); }
    public static RewardClaimResult pending(String code, String message) { return new RewardClaimResult(State.PENDING, code, message); }
    public static RewardClaimResult failure(String code, String message) { return new RewardClaimResult(State.FAILURE, code, message); }
}
