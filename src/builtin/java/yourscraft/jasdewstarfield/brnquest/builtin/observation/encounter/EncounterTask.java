package yourscraft.jasdewstarfield.brnquest.builtin.observation.encounter;
import yourscraft.jasdewstarfield.brnquest.reward.*;
import yourscraft.jasdewstarfield.brnquest.task.*;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigFieldDescriptor;
import java.util.*;

/** Observation commits only its completed latch; kill counts are written through the existing progress API. */
public record EncounterTask(boolean observe) implements TaskType<Map<String,String>> {
    public Codec<Map<String,String>> configCodec() { return EncounterConfig.codec(observe); }
    public List<ConfigFieldDescriptor> configFields() { return EncounterConfig.fields(observe); }
    public Map<String,String> normalizeConfig(Map<String,String> config) { return EncounterConfig.normalize(config); }
    public boolean satisfied(TaskContext context,Map<String,String> config) { return context.progress()>=(observe ? 1 : EncounterConfig.parse(config,false).count()); }
    public int pollingIntervalTicks() { return observe ? 1 : 0; }
    public long sampledProgress(TaskContext context,Map<String,String> config) {
        return context.progress()>=1 ? 1 : ObservationRuntime.sample(context,EncounterConfig.parse(config,true));
    }
    public void resetTransientState(TaskContext context) {
        if(observe) ObservationRuntime.clearTask(context);
    }
    public Component describe(TaskView task,Map<String,String> config) { return Component.literal(config.getOrDefault("target","")); }
}
