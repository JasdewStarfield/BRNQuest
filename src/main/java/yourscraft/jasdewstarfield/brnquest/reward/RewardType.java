package yourscraft.jasdewstarfield.brnquest.reward;

import com.mojang.serialization.Codec;
import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.data.RewardDefinition;

/** Public reward extension point with a declared immutable config codec. */
public interface RewardType<TConfig> {
    Codec<TConfig> configCodec();
    RewardResult execute(ServerPlayer player, RewardDefinition definition);
}
