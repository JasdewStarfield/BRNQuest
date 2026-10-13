package yourscraft.jasdewstarfield.brnquest.builtin.observation.statistics;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.editor.*;
import java.util.*;

/** Exact registry identities and raw vanilla units; opaque extension keys survive authoring. */
public record StatisticsConfig(ResourceLocation statType, ResourceLocation stat, int count) {
    public static final ResourceLocation ID = ResourceLocation.parse("brnquest:statistics");
    public static final List<String> TYPES = List.of("minecraft:custom", "minecraft:mined", "minecraft:crafted",
            "minecraft:used", "minecraft:broken", "minecraft:picked_up", "minecraft:dropped", "minecraft:killed", "minecraft:killed_by");
    public static final Codec<Map<String, String>> CODEC = Codec.unboundedMap(Codec.STRING, Codec.STRING).validate(values -> {
        try { parse(values); return DataResult.success(values); }
        catch (IllegalArgumentException error) { return DataResult.error(error::getMessage); }
    });

    public static StatisticsConfig parse(Map<String, String> values) {
        String category = values.getOrDefault("stat_type", "minecraft:custom").strip();
        if (!TYPES.contains(category)) throw new IllegalArgumentException("stat_type: expected one of the nine vanilla statistic categories");
        ResourceLocation stat = ResourceLocation.tryParse(values.getOrDefault("stat", "minecraft:jump").strip());
        if (stat == null) throw new IllegalArgumentException("stat: expected a registry ID");
        int count;
        try { count = Integer.parseInt(values.getOrDefault("count", "1").strip()); }
        catch (NumberFormatException error) { throw new IllegalArgumentException("count: expected 1..2147483647 raw units"); }
        if (count < 1) throw new IllegalArgumentException("count: expected 1..2147483647 raw units");
        // Reserve the mode field now so a future delta configuration cannot silently count history.
        if (!values.getOrDefault("mode", "total").strip().equals("total"))
            throw new IllegalArgumentException("mode: currently supported value is total");
        return new StatisticsConfig(ResourceLocation.parse(category), stat, count);
    }

    public static Map<String, String> normalize(Map<String, String> values) {
        var result = new TreeMap<>(values);
        for (String key : List.of("stat_type", "stat", "count", "mode")) result.computeIfPresent(key, (k, v) -> v.strip());
        parse(result);
        return result;
    }

    public static List<ConfigFieldDescriptor> fields() {
        var labels = new TreeMap<String, String>();
        TYPES.forEach(id -> labels.put(id, "stat_type." + id.replace(':', '.')));
        return List.of(
                ConfigFieldDescriptor.field("title", ConfigValueType.TEXT).withLabel("screen.brnquest.editor.config.title"),
                ConfigFieldDescriptor.enumeration("stat_type", TYPES).withDefault("minecraft:custom")
                        .withLabel("screen.brnquest.statistics.category").withValueLabels(labels),
                ConfigFieldDescriptor.field("stat", ConfigValueType.TEXT).asRequired().withDefault("minecraft:jump")
                        .withLabel("screen.brnquest.statistics.target").withServerSource(StatisticsTargets.SOURCE)
                        .withHelp("screen.brnquest.statistics.history_help").withValidator((value, config) -> {
                            // Validate the pair again after a category edit; matching IDs in another registry do not count.
                            var id = ResourceLocation.tryParse(value);
                            var category = config.getOrDefault("stat_type", "minecraft:custom");
                            return id != null && TYPES.contains(category) && StatisticsTargets.resolve(new StatisticsConfig(
                                    ResourceLocation.parse(category), id, 1)).isPresent() ? List.of()
                                    : List.of(new ConfigFieldIssue("stat", ConfigFieldIssue.Severity.ERROR,
                                    "STATISTIC_MISSING", "Statistic target is not registered in the selected category"));
                        }),
                ConfigFieldDescriptor.field("count", ConfigValueType.INTEGER).withDefault("1").withRange(1, Integer.MAX_VALUE)
                        .withLabel("screen.brnquest.statistics.count").withHelp("screen.brnquest.statistics.units_help"));
    }
}
