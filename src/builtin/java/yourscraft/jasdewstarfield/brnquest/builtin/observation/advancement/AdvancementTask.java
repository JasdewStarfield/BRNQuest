package yourscraft.jasdewstarfield.brnquest.builtin.observation.advancement;
import yourscraft.jasdewstarfield.brnquest.reward.*;
import yourscraft.jasdewstarfield.brnquest.task.*;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigFieldDescriptor;
import java.util.*;

/** Vanilla state can satisfy a newly eligible task; the owner ledger latches successful observations. */
public final class AdvancementTask implements TaskType<Map<String,String>> {
    public Codec<Map<String,String>> configCodec() { return AdvancementConfig.CODEC; }
    public List<ConfigFieldDescriptor> configFields() { return AdvancementConfig.fields(true); }
    public Map<String,String> normalizeConfig(Map<String,String> values) { return AdvancementConfig.normalize(values); }
    public boolean satisfied(TaskContext context, Map<String,String> config) { return context.progress() >= 1; }
    // Cached checks are cheap; invalidation events are consumed on the next tick, never inside award().
    public int pollingIntervalTicks() { return 1; }
    public long sampledProgress(TaskContext context, Map<String,String> config) {
        return context.progress() >= 1 || AdvancementTargets.matches(context.player(),AdvancementConfig.parse(config)) ? 1 : 0;
    }
    public Component describe(TaskView task, Map<String,String> config) { return Component.literal(config.getOrDefault("advancement", "")); }
}
