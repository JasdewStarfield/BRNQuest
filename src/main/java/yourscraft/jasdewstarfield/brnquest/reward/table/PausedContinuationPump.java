package yourscraft.jasdewstarfield.brnquest.reward.table;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** One pending wake-up per server. The supplied scheduler must dispatch back onto the server thread. */
final class PausedContinuationPump {
    private final BooleanSupplier paused;
    private final BooleanSupplier pending;
    private final Runnable drain;
    private final Consumer<Runnable> schedule;
    private boolean armed;
    private boolean cancelled;

    PausedContinuationPump(BooleanSupplier paused, BooleanSupplier pending, Runnable drain, Consumer<Runnable> schedule) {
        this.paused = paused;
        this.pending = pending;
        this.drain = drain;
        this.schedule = schedule;
    }

    void request() {
        if (armed || cancelled) return;
        armed = true;
        schedule.accept(this::wake);
    }

    private void wake() {
        if (cancelled) return;
        try {
            // Normal player ticks already drain the queue; pause alone needs this alternate delivery path.
            if (paused.getAsBoolean()) drain.run();
        } finally {
            armed = false;
            if (!cancelled && pending.getAsBoolean()) request();
        }
    }

    /** A delayed callback can still arrive after shutdown, but can neither execute nor reschedule work. */
    void cancel() { cancelled = true; }
}
