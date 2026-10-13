package legacy;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;
import yourscraft.jasdewstarfield.brnquest.task.*;
import java.util.Map;

/** Compiled against the frozen old interface; today's preparation/result methods must remain optional. */
public final class LegacySamplingTaskType implements TaskType<Map<String, String>> {
    public Codec<Map<String, String>> configCodec() { return Codec.unboundedMap(Codec.STRING, Codec.STRING); }
    public long sampledProgress(TaskContext context, Map<String, String> config) { return context.progress() + 3; }
    public boolean satisfied(TaskContext context, Map<String, String> config) { return false; }
    public Component describe(TaskView task, Map<String, String> config) { return Component.empty(); }
}
