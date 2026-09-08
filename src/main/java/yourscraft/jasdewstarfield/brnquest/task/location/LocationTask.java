package yourscraft.jasdewstarfield.brnquest.task.location;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;
import yourscraft.jasdewstarfield.brnquest.editor.*;
import yourscraft.jasdewstarfield.brnquest.task.*;
import java.util.*;

/** Four separately registered exploration types share mechanics, without core type-ID branches. */
public final class LocationTask implements TaskType<Map<String, String>> {
    private final String kind;
    public LocationTask(String kind) { this.kind = kind; }
    public Codec<Map<String, String>> configCodec() { return LocationConfig.codec(kind); }
    public boolean satisfied(TaskContext context, Map<String, String> config) { return context.progress() >= 1; }
    public int pollingIntervalTicks() { return 10; }
    public long sampledProgress(TaskContext context, Map<String, String> config) {
        return context.progress() >= 1 || LocationTargets.matches(context.player(), kind, LocationConfig.parse(kind, config)) ? 1 : 0;
    }
    public Component describe(TaskView task, Map<String, String> config) { return Component.literal(config.toString()); }
    public List<ConfigFieldDescriptor> configFields() {
        List<ConfigFieldDescriptor> fields = new ArrayList<>();
        fields.add(ConfigFieldDescriptor.field("title", ConfigValueType.TEXT).withLabel("screen.brnquest.editor.config.title"));
        String key = kind.equals("location") ? "dimension" : kind;
        var selector = ConfigFieldDescriptor.field(key, ConfigValueType.TEXT)
                .withDefault(key.equals("dimension") ? "minecraft:overworld" : key.equals("biome") ? "minecraft:plains" : "minecraft:village_plains")
                .withLabel("screen.brnquest.location." + key).withServerSource(ResourceLocation.parse("brnquest:" + key));
        // An ignored dimension may be blank; the codec still requires it when filtering is enabled.
        fields.add(kind.equals("location") ? selector : selector.asRequired());
        if (kind.equals("location")) {
            fields.add(ConfigFieldDescriptor.field("ignore_dimension", ConfigValueType.BOOLEAN).withDefault("false").withLabel("screen.brnquest.location.ignore_dimension"));
            fields.add(ConfigFieldDescriptor.field("position", ConfigValueType.INTEGER_VECTOR3).withDefault("0,0,0").withLabel("screen.brnquest.location.position")
                    .withServerSource(ResourceLocation.parse("brnquest:position")));
            fields.add(ConfigFieldDescriptor.field("size", ConfigValueType.INTEGER_VECTOR3).withRange(1, Integer.MAX_VALUE).withDefault("1,1,1").withLabel("screen.brnquest.location.size"));
        }
        return List.copyOf(fields);
    }
    public Map<String, String> normalizeConfig(Map<String, String> config) {
        var result = new TreeMap<>(config);
        for (String key : List.of("dimension", "biome", "structure", "position", "size")) result.computeIfPresent(key, (k, v) -> v.strip());
        return result;
    }
}
