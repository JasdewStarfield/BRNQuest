package yourscraft.jasdewstarfield.brnquest.reward;

import com.mojang.serialization.Codec;
import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;
import yourscraft.jasdewstarfield.brnquest.data.RewardDefinition;
import yourscraft.jasdewstarfield.brnquest.data.StringMapConfigCodec;

import java.util.Optional;

/** Public reward extension point with a declared immutable config codec. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public interface RewardType<TConfig> {
    Codec<TConfig> configCodec();
    RewardResult execute(ServerPlayer player, RewardDefinition definition, TConfig config);

    /** Returns a diagnostic message when schema-1 config cannot be decoded by this type. */
    default Optional<String> configError(RewardDefinition definition) {
        return StringMapConfigCodec.decode(configCodec(), definition.config()).error().map(error -> error.message());
    }

    default RewardResult executeDecoded(ServerPlayer player, RewardDefinition definition) {
        return StringMapConfigCodec.decode(configCodec(), definition.config()).result()
                .map(config -> execute(player, definition, config))
                .orElseGet(() -> RewardResult.failure("Invalid reward configuration"));
    }
}
