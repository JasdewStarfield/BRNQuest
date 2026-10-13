package yourscraft.jasdewstarfield.brnquest.compat.ftb;

import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.builtin.observation.statistics.StatisticsConfig;
import java.util.Map;

/** Independently converts the observed stat/value SNBT definition. Original values remain available for review. */
public final class FtbStatisticFields {
    private FtbStatisticFields() {}
    public static String apply(ResourceLocation type, CompoundTag raw, Map<String, String> config) {
        if (!type.equals(StatisticsConfig.ID)) return "";
        for (String field : java.util.List.of("stat", "value", "stat_type", "count", "mode", "format"))
            if (raw.contains(field)) config.put("ftb." + field, raw.get(field).toString());
        config.put("stat_type", "minecraft:custom");
        config.put("mode", "total");
        config.put("format", "raw");
        config.put("ftb.stat_namespace_fallback", "true");
        config.put("stat", raw.getString("stat"));
        String error = "";
        Tag value = raw.get("value");
        if (value instanceof NumericTag number && Double.isFinite(number.getAsDouble())
                && number.getAsDouble() == number.getAsLong() && number.getAsLong() >= 1 && number.getAsLong() <= Integer.MAX_VALUE) {
            config.put("count", Long.toString(number.getAsLong()));
        } else {
            config.put("count", raw.contains("value") ? raw.get("value").toString() : "");
            error = "Statistic value must be an integer from 1 to 2147483647";
        }
        if (!raw.contains("stat", Tag.TAG_STRING) || ResourceLocation.tryParse(raw.getString("stat")) == null)
            error = "Statistic stat must be a registry ID";
        if (!error.isEmpty()) config.put("ftb.stat_error", error);
        return error;
    }
}
