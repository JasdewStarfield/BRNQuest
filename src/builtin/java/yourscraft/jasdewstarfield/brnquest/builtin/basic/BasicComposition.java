package yourscraft.jasdewstarfield.brnquest.builtin.basic;

import yourscraft.jasdewstarfield.brnquest.api.RewardView;
import yourscraft.jasdewstarfield.brnquest.reward.*;
import java.util.Map;

/** Keeps legacy prepared maps and version 1 receipts compatible with the extracted basic rewards. */
final class BasicComposition implements ComposableReward {
    private final RewardType<Map<String, String>> type;
    private final String kind;
    BasicComposition(RewardType<Map<String, String>> type, String kind) {
        this.type = type;
        this.kind = kind;
    }
    public void validateConfig(Map<String, String> values) {
        if (!kind.equals("custom") && Integer.parseInt(values.getOrDefault(kind, "1")) < 1)
            throw new IllegalArgumentException("Experience must be positive");
    }
    public Map<String, String> prepare(RewardLeafContext leaf) {
        validateConfig(leaf.config());
        return Map.copyOf(leaf.config());
    }
    public RewardClaimResult execute(RewardLeafContext leaf, Map<String, String> prepared) {
        var root = leaf.root().rewardContext();
        // The coordinator owns occurrence receipts; retain the root ID only for diagnostics.
        var view = new RewardView(root.bookId(), root.reward().id(), leaf.typeId(), prepared, "manual", false);
        var result = type.execute(new RewardContext(root.player(), root.bookId(), root.questId(), view), prepared);
        return result.success() ? RewardClaimResult.success(result.message())
                : RewardClaimResult.failure("UNKNOWN", result.message());
    }
    // Default recover deliberately leaves an uncertain grant for administrator review.
}
