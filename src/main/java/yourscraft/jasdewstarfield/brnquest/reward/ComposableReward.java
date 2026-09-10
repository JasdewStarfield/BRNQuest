package yourscraft.jasdewstarfield.brnquest.reward;

import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import java.util.Map;

/** Explicit opt-in: preparation has no side effects; an uncertain execution is never replayed automatically. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public interface ComposableReward {
    /** Pure publication validation, including values that a permissive legacy codec accepts. */
    default void validateConfig(Map<String, String> config) {}
    /** Persistable prepared data. Validate resources, quantities and permissions here, without granting rewards. */
    Map<String, String> prepare(RewardLeafContext context) throws Exception;
    /** Execute once after a forced STARTED record; PENDING stops every later leaf. */
    RewardClaimResult execute(RewardLeafContext context, Map<String, String> prepared) throws Exception;
    /** Only durable external evidence may turn a started leaf into success after interruption. */
    default RewardClaimResult recover(RewardLeafContext context, Map<String, String> prepared) throws Exception {
        return RewardClaimResult.failure("UNKNOWN", "Execution may have had effects; administrator review required");
    }
    /** Change this token when persisted preparation is no longer compatible with the implementation. */
    default String version() { return "1"; }
}
