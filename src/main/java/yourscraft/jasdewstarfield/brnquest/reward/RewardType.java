package yourscraft.jasdewstarfield.brnquest.reward;

import com.mojang.serialization.Codec;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;

/** Public reward extension point with immutable context and a declared config codec. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public interface RewardType<TConfig> {
    Codec<TConfig> configCodec();
    RewardResult execute(RewardContext context, TConfig config);
}
