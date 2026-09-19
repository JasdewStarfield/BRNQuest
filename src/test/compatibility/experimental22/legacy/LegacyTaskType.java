package legacy;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;
import yourscraft.jasdewstarfield.brnquest.task.TaskContext;
import yourscraft.jasdewstarfield.brnquest.task.TaskType;
import java.util.Map;

/** Compiled only against the pre-context SPI to verify default dispatch on a real old consumer. */
public final class LegacyTaskType implements TaskType<Map<String, String>> {
    public int calls;
    public Codec<Map<String, String>> configCodec() { return Codec.unboundedMap(Codec.STRING, Codec.STRING); }
    public Map<String, String> normalizeConfig(Map<String, String> config) { calls++; return Map.of("legacy", "task"); }
    public boolean satisfied(TaskContext context, Map<String, String> config) { return true; }
    public Component describe(TaskView task, Map<String, String> config) { return Component.empty(); }
}
