package yourscraft.jasdewstarfield.brnquest.builtin.observation.statistics;

import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.task.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Checks independent member arithmetic, native reset boundaries and deduplication across persisted samples. */
class StatisticsCounterTest {
    private static final StatisticsConfig DELTA = StatisticsConfig.parse(Map.of("mode", "since_available", "count", "1000"));
    @Test void incrementsRebaseAfterNativeResetAndDoNotReplay() {
        var first = StatisticsCounter.sample(0, new TaskSamplingState(Map.of("last", 80L)), 95, DELTA);
        assertEquals(15, first.progress());
        var reset = StatisticsCounter.sample(first.progress(), first.state(), 0, DELTA);
        assertEquals(15, reset.progress());
        var later = StatisticsCounter.sample(reset.progress(), reset.state(), 5, DELTA);
        assertEquals(20, later.progress());
        assertEquals(20, later.state().values().get("contribution"));
        assertEquals(20, StatisticsCounter.sample(later.progress(), later.state(), 5, DELTA).progress());
    }
    @Test void sharedTotalsUseMaximumAndDeltaContributionsUseIndependentReadPositions() {
        var historical = StatisticsConfig.parse(Map.of("count", "1000"));
        var a = StatisticsCounter.sample(0, TaskSamplingState.EMPTY, 8, historical);
        var b = StatisticsCounter.sample(a.progress(), TaskSamplingState.EMPTY, 12, historical);
        assertEquals(12, b.progress());
        assertEquals(12, StatisticsCounter.sample(b.progress(), a.state(), 8, historical).progress());
        var first = StatisticsCounter.sample(0, new TaskSamplingState(Map.of("last", 50L)), 52, DELTA);
        var second = StatisticsCounter.sample(first.progress(), new TaskSamplingState(Map.of("last", 90L)), 93, DELTA);
        assertEquals(5, second.progress());
        assertEquals(5, StatisticsCounter.sample(second.progress(), first.state(), 52, DELTA).progress());
    }
    @Test void unpreparedDeltaCannotSwallowHistoryAndLargeProgressCannotOverflow() {
        assertEquals(0, StatisticsCounter.sample(0, TaskSamplingState.EMPTY, 500, DELTA).progress());
        assertEquals(Long.MAX_VALUE, StatisticsCounter.sample(Long.MAX_VALUE - 1,
                new TaskSamplingState(Map.of("last", 0L)), 20, DELTA).progress());
    }
    @Test void labelsThresholdAndFormattingKeepSemanticIdentity() {
        var type = new StatisticsTask();
        var raw = Map.of("stat", "minecraft:jump");
        var changed = Map.of("stat", "minecraft:jump", "count", "40", "title", "Changed", "format", "raw");
        assertEquals(type.samplingIdentity(new StatisticsTask.Config(StatisticsConfig.parse(raw), raw)),
                type.samplingIdentity(new StatisticsTask.Config(StatisticsConfig.parse(changed), changed)));
        var delta = Map.of("stat", "minecraft:jump", "mode", "since_available");
        assertNotEquals(type.samplingIdentity(new StatisticsTask.Config(StatisticsConfig.parse(raw), raw)),
                type.samplingIdentity(new StatisticsTask.Config(StatisticsConfig.parse(delta), delta)));
    }
}
