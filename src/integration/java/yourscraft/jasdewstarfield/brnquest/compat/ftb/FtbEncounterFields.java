package yourscraft.jasdewstarfield.brnquest.compat.ftb;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.builtin.observation.encounter.EncounterConfig;
import java.util.*;

/** Explicit conversions preserve unsupported source constraints instead of silently broadening a target. */
final class FtbEncounterFields {
    private static final List<String> MODES=List.of("block","block_tag","block_state","block_entity","block_entity_type","entity_type","entity_type_tag");
    private FtbEncounterFields() {}
    static String apply(ResourceLocation type,CompoundTag raw,Map<String,String> config) {
        if(!type.equals(EncounterConfig.OBSERVE) && !type.equals(EncounterConfig.KILL)) return "";
        try {
            if(type.equals(EncounterConfig.KILL)) {
                String tag=raw.getString("entityTypeTag");
                config.put("target",tag.isBlank() ? raw.contains("entity") ? raw.getString("entity") : "minecraft:zombie" : prefix(tag));
                config.put("count",raw.contains("value") ? Long.toString(raw.getLong("value")) : "100");
                if(!raw.getString("custom_name").isBlank() || !raw.getString("nbt_filter").isBlank()) throw new IllegalArgumentException("Kill name/NBT constraints require manual conversion");
            } else {
                String mode=raw.contains("observation_type") ? raw.getString("observation_type") : "block";
                if(raw.contains("observe_type")) {
                    int ordinal=raw.getInt("observe_type");
                    if(ordinal<0 || ordinal>=MODES.size()) throw new IllegalArgumentException("Invalid observe_type ordinal");
                    String legacy=MODES.get(ordinal);
                    if(raw.contains("observation_type") && !mode.equals(legacy)) config.put("ftb.observation_mode_conflict",mode+" -> "+legacy);
                    mode=legacy;
                }
                if(!Set.of("block","block_tag","entity_type","entity_type_tag").contains(mode)) throw new IllegalArgumentException("Unsupported observation mode: "+mode);
                String target=raw.contains("to_observe") ? raw.getString("to_observe") : "minecraft:dirt";
                config.put("kind",mode.startsWith("block") ? "block" : "entity");
                config.put("target",mode.endsWith("_tag") ? prefix(target) : target);
                config.put("duration",Long.toString(raw.getLong("timer")));
                config.put("distance","8");
            }
            EncounterConfig.parse(config,type.equals(EncounterConfig.OBSERVE));
            return "";
        } catch(IllegalArgumentException error) {
            config.put("ftb.encounter_error",error.getMessage());
            return error.getMessage();
        }
    }
    private static String prefix(String tag) { return tag.startsWith("#") ? tag : "#"+tag; }
}
