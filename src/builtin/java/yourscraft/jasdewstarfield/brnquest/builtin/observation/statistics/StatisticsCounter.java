package yourscraft.jasdewstarfield.brnquest.builtin.observation.statistics;

import yourscraft.jasdewstarfield.brnquest.task.*;
import java.util.Map;

/** Pure arithmetic over one member's raw counter; persistence and shared ledger routing stay in the engine. */
public final class StatisticsCounter {
    private StatisticsCounter() {}
    public static TaskSamplingResult sample(long progress, TaskSamplingState state, long raw, StatisticsConfig config) {
        raw = Math.max(0, raw);
        if (config.mode().equals("total")) {
            // Historical team progress is the highest member history, so repeated visits cannot add it again.
            long next = Math.max(progress, Math.min(raw, config.count()));
            return new TaskSamplingResult(next, new TaskSamplingState(Map.of("last", raw, "contribution", Math.min(raw, config.count()))));
        }
        if (!state.values().containsKey("last")) return new TaskSamplingResult(progress, state);
        long delta = Math.max(0, raw - state.values().get("last"));
        long contributed = state.values().getOrDefault("contribution", 0L);
        return new TaskSamplingResult(add(progress, delta), new TaskSamplingState(Map.of("last", raw, "contribution", add(contributed, delta))));
    }
    private static long add(long value, long delta) { return value > Long.MAX_VALUE - delta ? Long.MAX_VALUE : value + delta; }
}
