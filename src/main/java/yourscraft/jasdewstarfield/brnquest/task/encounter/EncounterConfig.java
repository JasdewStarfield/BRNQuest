package yourscraft.jasdewstarfield.brnquest.task.encounter;

import com.mojang.serialization.*;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.editor.*;
import java.util.*;

/** Type-owned author schema; unknown extension fields survive normalization and serialization. */
public record EncounterConfig(String kind, String selector, double distance, int duration, long count) {
    public static final ResourceLocation OBSERVE = ResourceLocation.parse("brnquest:observe");
    public static final ResourceLocation KILL = ResourceLocation.parse("brnquest:kill_entity");
    public static Codec<Map<String,String>> codec(boolean observe) {
        return Codec.unboundedMap(Codec.STRING,Codec.STRING).validate(values -> {
            try { parse(values,observe); return DataResult.success(values); }
            catch (IllegalArgumentException error) { return DataResult.error(error::getMessage); }
        });
    }
    public static EncounterConfig parse(Map<String,String> values, boolean observe) {
        if(values.containsKey("ftb.encounter_error")) throw new IllegalArgumentException(values.get("ftb.encounter_error"));
        String kind=observe ? values.getOrDefault("kind","block").strip() : "entity";
        if (!kind.equals("block") && !kind.equals("entity")) throw new IllegalArgumentException("kind: expected block/entity");
        String selector=values.getOrDefault("target",observe ? "minecraft:dirt" : "minecraft:zombie").strip();
        if (ResourceLocation.tryParse(selector.startsWith("#") ? selector.substring(1) : selector)==null) throw new IllegalArgumentException("target: expected ID or #tag");
        double distance=observe ? Double.parseDouble(values.getOrDefault("distance","8")) : 8;
        int duration=observe ? Integer.parseInt(values.getOrDefault("duration","0")) : 0;
        long count=observe ? 1 : Long.parseLong(values.getOrDefault("count","1"));
        if (!Double.isFinite(distance) || distance<=0 || distance>64) throw new IllegalArgumentException("distance: expected 0 < distance <= 64");
        if (duration<0 || duration>1200) throw new IllegalArgumentException("duration: expected 0..1200 ticks");
        if (count<1) throw new IllegalArgumentException("count: expected a positive integer");
        return new EncounterConfig(kind,selector,distance,duration,count);
    }
    public static Map<String,String> normalize(Map<String,String> values) {
        var result=new TreeMap<>(values);
        for(String key:List.of("kind","target","distance","duration","count")) result.computeIfPresent(key,(k,v)->v.strip());
        return result;
    }
    public static List<ConfigFieldDescriptor> fields(boolean observe) {
        var fields=new ArrayList<ConfigFieldDescriptor>();
        fields.add(ConfigFieldDescriptor.field("title",ConfigValueType.TEXT).withLabel("screen.brnquest.editor.config.title"));
        if(observe) fields.add(ConfigFieldDescriptor.enumeration("kind",List.of("block","entity")).withDefault("block")
                .withLabel("screen.brnquest.encounter.kind").withValueLabels(Map.of("block","screen.brnquest.encounter.block","entity","screen.brnquest.encounter.entity")));
        fields.add(ConfigFieldDescriptor.field("target",ConfigValueType.TEXT).asRequired().withDefault(observe ? "minecraft:dirt" : "minecraft:zombie")
                .withLabel("screen.brnquest.encounter.target").withServerSource(observe ? EncounterTargets.OBSERVE_SOURCE : EncounterTargets.ENTITY_SOURCE));
        if(observe) {
            fields.add(ConfigFieldDescriptor.field("distance",ConfigValueType.DECIMAL).withDefault("8").withRange(0.01,64).withLabel("screen.brnquest.encounter.distance"));
            fields.add(ConfigFieldDescriptor.field("duration",ConfigValueType.INTEGER).withDefault("0").withRange(0,1200).withLabel("screen.brnquest.encounter.duration").withHelp("screen.brnquest.encounter.observe_help"));
        } else fields.add(ConfigFieldDescriptor.field("count",ConfigValueType.INTEGER).withDefault("1").withRange(1,Long.MAX_VALUE).withLabel("screen.brnquest.encounter.count").withHelp("screen.brnquest.encounter.kill_help"));
        return List.copyOf(fields);
    }
}
