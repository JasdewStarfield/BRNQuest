package yourscraft.jasdewstarfield.brnquest.task;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.data.TaskDefinition;
import yourscraft.jasdewstarfield.brnquest.progress.PlayerProgress;

/** Public task extension point for config decoding, tracking and read-only descriptions. */
public interface TaskType<TConfig> {
    Codec<TConfig> configCodec();
    boolean satisfied(ServerPlayer player, TaskDefinition definition, PlayerProgress progress);
    default boolean consume(ServerPlayer player, TaskDefinition definition) { return true; }
    Component describe(TaskDefinition definition);
}
