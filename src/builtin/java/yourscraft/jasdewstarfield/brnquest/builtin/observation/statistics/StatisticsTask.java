package yourscraft.jasdewstarfield.brnquest.builtin.observation.statistics;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;
import yourscraft.jasdewstarfield.brnquest.editor.*;
import yourscraft.jasdewstarfield.brnquest.owner.*;
import yourscraft.jasdewstarfield.brnquest.task.*;
import java.util.*;

/** Personal historical counters are sampled read-only; the engine persists only increases. */
public final class StatisticsTask implements TaskType<Map<String, String>> {
    public Codec<Map<String, String>> configCodec() { return StatisticsConfig.CODEC; }
    public List<ConfigFieldDescriptor> configFields() { return StatisticsConfig.fields(); }
    public Map<String, String> normalizeConfig(Map<String, String> values) { return StatisticsConfig.normalize(values); }
    public Map<String, String> normalizeConfig(ConfigNormalizationContext context, Map<String, String> values) {
        var result = normalizeConfig(values);
        if (context.registries().isPresent() && StatisticsTargets.resolve(StatisticsConfig.parse(result)).isEmpty())
            throw new IllegalArgumentException("stat: no registered statistic in the selected category");
        return result;
    }
    public int pollingIntervalTicks() { return 20; }
    public long sampledProgress(TaskContext context, Map<String, String> values) {
        var config = StatisticsConfig.parse(values);
        // Completed objectives need no further reads while other objectives keep the quest open.
        if (context.progress() >= config.count()) return context.progress();
        // F1c will add member contribution accounting. Until then no historical counter enters a shared ledger.
        if (context.player() == null || !ProgressOwnerService.require(context.player()).providerId().equals(ProgressOwnerProviders.PERSONAL))
            return context.progress();
        return StatisticsTargets.resolve(config)
                .map(stat -> Math.max(context.progress(), context.player().getStats().getValue(stat)))
                .orElse(context.progress());
    }
    public boolean satisfied(TaskContext context, Map<String, String> values) {
        var config = StatisticsConfig.parse(values);
        return StatisticsTargets.resolve(config).isPresent() && context.progress() >= config.count();
    }
    public Component describe(TaskView task, Map<String, String> values) {
        var config = StatisticsConfig.parse(values);
        return Component.translatable("screen.brnquest.statistics.requirement", StatisticsTargets.categoryName(config),
                StatisticsTargets.targetName(config), StatisticsTargets.format(config, config.count()));
    }
}
