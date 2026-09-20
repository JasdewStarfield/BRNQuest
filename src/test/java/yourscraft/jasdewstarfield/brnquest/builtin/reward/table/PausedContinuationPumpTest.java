package yourscraft.jasdewstarfield.brnquest.builtin.reward.table;
import yourscraft.jasdewstarfield.brnquest.builtin.reward.*;

import org.junit.jupiter.api.Test;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class PausedContinuationPumpTest {
    @Test void pausedServerCompletesMultipleBatchesWithoutPlayerTicksOrRepeatClicks() {
        var callbacks = new ArrayDeque<Runnable>();
        var remaining = new AtomicInteger(19);
        var effects = new AtomicInteger();
        var pump = new PausedContinuationPump(() -> true, () -> remaining.get() > 0, () -> {
            int count = Math.min(8, remaining.get());
            remaining.addAndGet(-count);
            effects.addAndGet(count);
        }, callbacks::add);
        pump.request(); pump.request(); pump.request();
        assertEquals(1, callbacks.size(), "repeat requests share one wake-up");
        callbacks.remove().run();
        assertEquals(8, effects.get());
        assertEquals(1, callbacks.size(), "remaining work schedules another bounded batch");
        callbacks.remove().run(); callbacks.remove().run();
        assertEquals(19, effects.get());
        assertTrue(callbacks.isEmpty(), "completion stops polling");
    }

    @Test void runningTicksAndLogoutDoNotLeaveASecondExecutor() {
        var callbacks = new ArrayDeque<Runnable>();
        var pending = new AtomicBoolean(true);
        var effects = new AtomicInteger();
        var pump = new PausedContinuationPump(() -> false, pending::get, effects::incrementAndGet, callbacks::add);
        pump.request(); callbacks.remove().run();
        assertEquals(0, effects.get(), "normal player ticks remain the execution owner");
        pending.set(false); // Normal completion or logout removed the queued continuation.
        callbacks.remove().run();
        assertTrue(callbacks.isEmpty());
        pump.request(); pump.cancel(); callbacks.remove().run();
        assertEquals(0, effects.get());
        assertTrue(callbacks.isEmpty(), "stale shutdown wake-ups cannot restart work");
    }

    @Test void frozenGameTickStillHasBoundedExecutionWindows() {
        assertEquals(RewardTableService.ExecutionWindow.at(42, true, 100_000_000L),
                RewardTableService.ExecutionWindow.at(42, true, 149_999_999L));
        assertNotEquals(RewardTableService.ExecutionWindow.at(42, true, 100_000_000L),
                RewardTableService.ExecutionWindow.at(42, true, 150_000_000L));
        assertEquals(RewardTableService.ExecutionWindow.at(42, false, 100_000_000L),
                RewardTableService.ExecutionWindow.at(42, false, 950_000_000L), "slow running ticks do not gain more budget");
        assertNotEquals(RewardTableService.ExecutionWindow.at(42, false, 100_000_000L),
                RewardTableService.ExecutionWindow.at(43, false, 100_000_000L));
    }
}
