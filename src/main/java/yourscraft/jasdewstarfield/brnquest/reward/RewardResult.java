package yourscraft.jasdewstarfield.brnquest.reward;

/** Structured result returned by a reward implementation. */
public record RewardResult(boolean success, String message) {
    public static RewardResult success(String message) { return new RewardResult(true, message); }
    public static RewardResult failure(String message) { return new RewardResult(false, message); }
}
