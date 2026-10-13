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
        return type(config.statType().toString()).flatMap(type -> resolve(type, config.stat()));
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
            return Component.translatableWithFallback("stat." + config.stat().toString().replace(':', '.'), config.stat().toString());
        }).orElse(Component.literal(config.stat().toString()));
    }

    public static Component categoryName(StatisticsConfig config) {
        return type(config.statType().toString()).map(StatType::getDisplayName)
                .orElse(Component.literal(config.statType().toString()));
    }

    public static String format(StatisticsConfig config, long value) {
        int bounded = (int) Math.clamp(value, 0, Integer.MAX_VALUE);
        return resolve(config).map(stat -> stat.format(bounded)).orElse(Integer.toString(bounded));
    }

    public static void register(BrnQuestExtensionRegistrar registrar) {
        registrar.fieldSource(SOURCE, new ServerFieldSources.Source() {
            public ServerFieldSources.Result query(ServerPlayer player, String filter, String selected) {
                return queryPage(player, filter, selected, 0, Map.of());
            }
            public ServerFieldSources.Result queryPage(ServerPlayer player, String filter, String selected,
                                                       int offset, Map<String, String> context) {
                return page(context.getOrDefault("stat_type", "minecraft:custom"), filter, selected, offset);
            }
        });
    }

    /** Browsing enumerates registry IDs only; it never creates a Stat for every candidate or scans player counters. */
    public static ServerFieldSources.Result page(String category, String filter, String selected, int offset) {
        var type = type(category).orElse(null);
        if (type == null) return new ServerFieldSources.Result(List.of(), 0, 0, "missing", "", category);
        String search = filter.toLowerCase(Locale.ROOT);
        var choices = type.getRegistry().keySet().stream().map(ResourceLocation::toString).sorted()
                .filter(id -> id.contains(search)).toList();
        var id = ResourceLocation.tryParse(selected);
        int count = id != null && type.getRegistry().containsKey(id) ? 1 : 0;
        return new ServerFieldSources.Result(choices.stream().skip(Math.max(0, offset)).limit(ServerFieldSources.PAGE_SIZE)
                .map(value -> new ServerFieldSources.Entry(value, 1)).toList(), choices.size(), count,
                selected.isBlank() || count == 1 ? "" : "missing", "", selected);
    }
}
