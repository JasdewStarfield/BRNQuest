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
    /** Optional editor metadata; an empty list selects the safe raw-config fallback. */
    default List<ConfigFieldDescriptor> configFields() { return List.of(); }
    /** Optional advanced claim path. The handler owns attempt safety; core retains eligibility and the final ledger. */
    default java.util.Optional<RewardClaimHandler> claimHandler() { return java.util.Optional.empty(); }
    RewardResult execute(RewardContext context, TConfig config);
}
