package yourscraft.jasdewstarfield.brnquest.reward;

import com.mojang.serialization.Codec;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigFieldDescriptor;

import java.util.List;

/** Public reward extension point with immutable context and a declared config codec. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public interface RewardType<TConfig> {
    Codec<TConfig> configCodec();
    /** Pure author-write normalization. Preserve keys the type does not own; never perform side effects. */
    default java.util.Map<String, String> normalizeConfig(java.util.Map<String, String> config) { return config; }

    /**
     * Normalizes owned fields with the current registry lookup when available. The default invokes
     * the legacy hook exactly once, keeping existing implementations source and binary compatible.
     * Keep this operation pure and repeatable; throw IllegalArgumentException for invalid input.
     * Return owned changes only or a full map: authoring merges them over the original opaque keys.
     */
    default java.util.Map<String, String> normalizeConfig(
            yourscraft.jasdewstarfield.brnquest.editor.ConfigNormalizationContext context,
            java.util.Map<String, String> config) {
        return normalizeConfig(config);
    }

    /** Optional editor metadata; an empty list selects the safe raw-config fallback. */
    default List<ConfigFieldDescriptor> configFields() { return List.of(); }
    /** Optional advanced claim path. The handler owns attempt safety; core retains eligibility and the final ledger. */
    default java.util.Optional<RewardClaimHandler> claimHandler() { return java.util.Optional.empty(); }
    /** Legacy extensions stay top-level only until they explicitly declare safe composition boundaries. */
    default java.util.Optional<ComposableReward> composition() { return java.util.Optional.empty(); }
    /** Interactive rewards require an explicit player claim; automatic triggers must skip them. */
    default boolean requiresManualClaim(java.util.Map<String, String> config) { return false; }
    RewardResult execute(RewardContext context, TConfig config);
}
