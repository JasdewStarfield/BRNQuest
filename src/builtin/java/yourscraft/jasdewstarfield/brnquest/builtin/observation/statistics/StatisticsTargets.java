package yourscraft.jasdewstarfield.brnquest.builtin.observation.statistics;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import yourscraft.jasdewstarfield.brnquest.editor.ServerFieldSources;
import yourscraft.jasdewstarfield.brnquest.extension.BrnQuestExtensionRegistrar;
import java.util.*;

/** Server and client resolve the same exact registry identity, without default-air fallbacks. */
public final class StatisticsTargets {
    public static final ResourceLocation SOURCE = ResourceLocation.parse("brnquest:statistics_targets");
    private StatisticsTargets() {}

    public static Optional<Stat<?>> resolve(StatisticsConfig config) {
        var exact = type(config.statType().toString()).flatMap(type -> resolve(type, config.stat()));
        // Only imported legacy custom-stat references opt into the observed vanilla-namespace workaround.
        if (exact.isEmpty() && config.legacyNamespace() && config.statType().toString().equals("minecraft:custom"))
            return type("minecraft:custom").flatMap(type -> resolve(type, ResourceLocation.withDefaultNamespace(config.stat().getPath())));
        return exact;
    }

    private static Optional<StatType<?>> type(String category) {
        if (!StatisticsConfig.TYPES.contains(category)) return Optional.empty();
        return BuiltInRegistries.STAT_TYPE.getOptional(ResourceLocation.parse(category));
    }

    private static <T> Optional<Stat<?>> resolve(StatType<T> type, ResourceLocation id) {
        // Existing custom Stat instances carry TIME/DISTANCE/DIVIDE_BY_TEN formatters.
        return type.getRegistry().getOptional(id).map(type::get);
    }

    public static Component targetName(StatisticsConfig config) {
        return resolve(config).map(stat -> {
            Object value = stat.getValue();
            if (value instanceof Block block) return block.getName();
            if (value instanceof Item item) return item.getDescription();
            if (value instanceof EntityType<?> entity) return entity.getDescription();
            String id = value instanceof ResourceLocation location ? location.toString() : config.stat().toString();
            return Component.translatableWithFallback("stat." + id.replace(':', '.'), id);
        }).orElse(Component.literal(config.stat().toString()));
    }

    public static Component categoryName(StatisticsConfig config) {
        return type(config.statType().toString()).map(StatType::getDisplayName)
                .orElse(Component.literal(config.statType().toString()));
    }

    public static String format(StatisticsConfig config, long value) {
        int bounded = (int) Math.clamp(value, 0, Integer.MAX_VALUE);
        if (config.rawFormat()) return Integer.toString(bounded);
        return resolve(config).map(stat -> stat.format(bounded)).orElse(Integer.toString(bounded));
    }
    /** A delta objective names its activation boundary in every detailed requirement and notification. */
    public static Component requirement(StatisticsConfig config) {
        return Component.translatable(config.mode().equals("since_available") ? "screen.brnquest.statistics.requirement_delta"
                : "screen.brnquest.statistics.requirement", categoryName(config), targetName(config), format(config, config.count()));
    }

    public static void register(BrnQuestExtensionRegistrar registrar) {
        registrar.fieldSource(SOURCE, new ServerFieldSources.Source() {
            public ServerFieldSources.Result query(ServerPlayer player, String filter, String selected) {
                return queryPage(player, filter, selected, 0, Map.of());
            }
            public ServerFieldSources.Result queryPage(ServerPlayer player, String filter, String selected,
                                                       int offset, Map<String, String> context) {
                return page(context.getOrDefault("stat_type", "minecraft:custom"), filter, selected, offset,
                        context.getOrDefault("ftb.stat_namespace_fallback", "false").equals("true"));
            }
        });
    }

    /** Browsing enumerates registry IDs only; it never creates a Stat for every candidate or scans player counters. */
    public static ServerFieldSources.Result page(String category, String filter, String selected, int offset) {
        return page(category, filter, selected, offset, false);
    }
    private static ServerFieldSources.Result page(String category, String filter, String selected, int offset, boolean legacy) {
        var type = type(category).orElse(null);
        if (type == null) return new ServerFieldSources.Result(List.of(), 0, 0, "missing", "", category);
        String search = filter.toLowerCase(Locale.ROOT);
        var choices = type.getRegistry().keySet().stream().map(ResourceLocation::toString).sorted()
                .filter(id -> id.contains(search)).toList();
        var id = ResourceLocation.tryParse(selected);
        int count = id != null && type.getRegistry().containsKey(id) ? 1 : 0;
        if (count == 0 && id != null && legacy && category.equals("minecraft:custom")
                && type.getRegistry().containsKey(ResourceLocation.withDefaultNamespace(id.getPath()))) count = 1;
        return new ServerFieldSources.Result(choices.stream().skip(Math.max(0, offset)).limit(ServerFieldSources.PAGE_SIZE)
                .map(value -> new ServerFieldSources.Entry(value, 1)).toList(), choices.size(), count,
                selected.isBlank() || count == 1 ? "" : "missing", "", selected);
    }
}
