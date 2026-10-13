package yourscraft.jasdewstarfield.brnquest.builtin.observation.statistics;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.editor.*;
import java.util.*;

/** Exact registry identities and raw vanilla units; opaque extension keys survive authoring. */
public record StatisticsConfig(ResourceLocation statType, ResourceLocation stat, int count, String mode, boolean rawFormat, boolean legacyNamespace) {
    public StatisticsConfig(ResourceLocation statType, ResourceLocation stat, int count) {
        this(statType, stat, count, "total", false, false);
    }
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
        String mode = values.getOrDefault("mode", "total").strip();
        if (!List.of("total", "since_available").contains(mode)) throw new IllegalArgumentException("mode: expected total/since_available");
        String format = values.getOrDefault("format", "native").strip();
        if (!List.of("native", "raw").contains(format)) throw new IllegalArgumentException("format: expected native/raw");
        return new StatisticsConfig(ResourceLocation.parse(category), stat, count, mode, format.equals("raw"),
                values.getOrDefault("ftb.stat_namespace_fallback", "false").equals("true"));
    }

    public static Map<String, String> normalize(Map<String, String> values) {
        var result = new TreeMap<>(values);
        for (String key : List.of("stat_type", "stat", "count", "mode", "format")) result.computeIfPresent(key, (k, v) -> v.strip());
        parse(result);
        return result;
    }

    public static List<ConfigFieldDescriptor> fields() {
        var labels = new TreeMap<String, String>();
        // Vanilla custom/kill titles may be missing or require entity arguments; categories need standalone labels.
        TYPES.forEach(id -> labels.put(id, categoryLabel(ResourceLocation.parse(id))));
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
                            boolean valid = false;
                            if (id != null && TYPES.contains(category)) {
                                var selection = new TreeMap<>(config);
                                selection.put("stat", value);
                                selection.put("count", "1");
                                try { valid = StatisticsTargets.resolve(parse(selection)).isPresent(); }
                                catch (IllegalArgumentException ignored) { /* Other fields retain their own diagnostics. */ }
                            }
                            return valid ? List.of()
                                    : List.of(new ConfigFieldIssue("stat", ConfigFieldIssue.Severity.ERROR,
                                    "STATISTIC_MISSING", "Statistic target is not registered in the selected category"));
                        }),
                ConfigFieldDescriptor.field("count", ConfigValueType.INTEGER).withDefault("1").withRange(1, Integer.MAX_VALUE)
                        .withLabel("screen.brnquest.statistics.count").withHelp("screen.brnquest.statistics.units_help"),
                ConfigFieldDescriptor.enumeration("mode", List.of("total", "since_available")).withDefault("total")
                        .withLabel("screen.brnquest.statistics.mode").withValueLabels(Map.of("total", "screen.brnquest.statistics.total",
                                "since_available", "screen.brnquest.statistics.since_available")).withHelp("screen.brnquest.statistics.mode_help"),
                ConfigFieldDescriptor.enumeration("format", List.of("native", "raw")).withDefault("native")
                        .withLabel("screen.brnquest.statistics.format").withValueLabels(Map.of("native", "screen.brnquest.statistics.native",
                                "raw", "screen.brnquest.statistics.raw")));
    }

    /** The picker, requirements and HUD share the same argument-free category names. */
    public static String categoryLabel(ResourceLocation type) {
        return "screen.brnquest.statistics.category." + type.getPath();
    }
}
