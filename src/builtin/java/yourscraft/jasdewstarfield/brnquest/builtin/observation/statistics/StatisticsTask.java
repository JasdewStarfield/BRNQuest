package yourscraft.jasdewstarfield.brnquest.builtin.observation.statistics;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;
import yourscraft.jasdewstarfield.brnquest.editor.*;
import yourscraft.jasdewstarfield.brnquest.task.*;
import java.util.*;

/** Independent server statistic sampler: historical maximum or durable per-member increments. */
public final class StatisticsTask implements ReusableTaskConfigType<StatisticsTask.Config> {
    /** Deeply immutable decoding lets the core reuse validation and the parsed counter settings. */
    public record Config(StatisticsConfig settings, Map<String, String> raw, String identity) {
        public Config { raw = Map.copyOf(raw); }
        public Config(StatisticsConfig settings, Map<String, String> raw) {
            this(settings, raw, settings.statType() + "/" + settings.stat() + "/" + settings.mode() + "/" + settings.legacyNamespace());
        }
    }
    public Codec<Config> configCodec() {
        return StatisticsConfig.CODEC.xmap(raw -> new Config(StatisticsConfig.parse(raw), raw), Config::raw);
    }
    public List<ConfigFieldDescriptor> configFields() { return StatisticsConfig.fields(); }
    public Map<String, String> normalizeConfig(Map<String, String> values) { return StatisticsConfig.normalize(values); }
    public Map<String, String> normalizeConfig(ConfigNormalizationContext context, Map<String, String> values) {
        var result = normalizeConfig(values);
        if (context.registries().isPresent() && StatisticsTargets.resolve(StatisticsConfig.parse(result)).isEmpty())
            throw new IllegalArgumentException("stat: no registered statistic in the selected category");
        return result;
    }
    public int pollingIntervalTicks() { return 3; }
    public boolean usesSamplingState() { return true; }
    public String samplingIdentity(Config values) {
        return values.identity();
    }
    public TaskSamplingResult prepareSample(TaskSamplingContext context, Config values) {
        var config = values.settings();
        if (context.state().values().containsKey("last") && !context.resuming()) return new TaskSamplingResult(context.task().progress(), context.state());
        var stat = StatisticsTargets.resolve(config).orElse(null);
        if (stat == null || context.task().player() == null) return new TaskSamplingResult(context.task().progress(), context.state(), false);
        long raw = read(context, config, stat);
        // A joining member keeps the shared aggregate; a new personal delta mode starts with no inherited history.
        long progress = config.mode().equals("since_available") && !context.shared() && !context.resuming() ? 0 : context.task().progress();
        return new TaskSamplingResult(progress, new TaskSamplingState(Map.of("last", raw,
                "contribution", context.state().values().getOrDefault("contribution", 0L))));
    }
    public TaskSamplingResult sample(TaskSamplingContext context, Config values) {
        var config = values.settings();
        if (context.task().progress() >= config.count()) return new TaskSamplingResult(context.task().progress(), context.state());
        var stat = StatisticsTargets.resolve(config).orElse(null);
        if (stat == null || context.task().player() == null) return new TaskSamplingResult(context.task().progress(), context.state());
        return StatisticsCounter.sample(context.task().progress(), context.state(), read(context, config, stat), config);
    }
    private static long read(TaskSamplingContext context, StatisticsConfig config, net.minecraft.stats.Stat<?> stat) {
        return context.values().get("brnquest:stat/" + stat.getName(),
                () -> Math.max(0, context.task().player().getStats().getValue(stat)));
    }
    /** The old numeric entry remains useful to stateless callers for historical totals. */
    public long sampledProgress(TaskContext context, Config values) {
        var config = values.settings();
        if (config.mode().equals("since_available") || context.player() == null) return context.progress();
        return StatisticsTargets.resolve(config).map(stat -> Math.max(context.progress(), Math.min(config.count(), context.player().getStats().getValue(stat))))
                .orElse(context.progress());
    }
    /** Convenience bridge for definition-only diagnostics and old internal test callers. */
    public long sampledProgress(TaskContext context, Map<String, String> raw) {
        return sampledProgress(context, new Config(StatisticsConfig.parse(raw), raw));
    }
    public boolean satisfied(TaskContext context, Map<String, String> raw) {
        return satisfied(context, new Config(StatisticsConfig.parse(raw), raw));
    }
    public boolean satisfied(TaskContext context, Config values) {
        var config = values.settings();
        return StatisticsTargets.resolve(config).isPresent() && context.progress() >= config.count();
    }
    public Component describe(TaskView task, Config values) {
        var config = values.settings();
        return StatisticsTargets.requirement(config);
    }
}
