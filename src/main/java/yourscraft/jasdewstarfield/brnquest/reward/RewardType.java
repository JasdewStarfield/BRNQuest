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
    /** Optional editor metadata; an empty list selects the safe raw-config fallback. */
    default List<ConfigFieldDescriptor> configFields() { return List.of(); }
    RewardResult execute(RewardContext context, TConfig config);
}
