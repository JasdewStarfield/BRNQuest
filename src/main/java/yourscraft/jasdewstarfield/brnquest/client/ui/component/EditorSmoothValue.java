package yourscraft.jasdewstarfield.brnquest.client.ui.component;

/**
 * Small deterministic animation state for continuous editor values.
 * Direct-manipulation paths can {@link #snap(double)} so hit testing never trails the cursor.
 */
public final class EditorSmoothValue {
    private static final double DEFAULT_RESPONSE_PER_SECOND = 12.0;
    private static final double DEFAULT_SETTLE_EPSILON = 0.01;

    private final double settleEpsilon;
    private double current;
    private double target;

    public EditorSmoothValue(double initialValue) {
        this(initialValue, DEFAULT_SETTLE_EPSILON);
    }

    public EditorSmoothValue(double initialValue, double settleEpsilon) {
        this.settleEpsilon = Math.max(0.0, settleEpsilon);
        snap(initialValue);
    }

    /**
     * Advances once per rendered frame. Exponential response keeps the duration stable
     * across frame rates, unlike applying one fixed fraction per client tick or frame.
     */
    public double advanceFrame(double elapsedSeconds) {
        return advanceFrame(elapsedSeconds, DEFAULT_RESPONSE_PER_SECOND);
    }

    public double advanceFrame(double elapsedSeconds, double responsePerSecond) {
        double remaining = target - current;
        if (Math.abs(remaining) <= settleEpsilon) {
            current = target;
            return current;
        }
        double seconds = Math.max(0.0, Math.min(0.1, elapsedSeconds));
        double response = 1.0 - Math.exp(-Math.max(0.0, responsePerSecond) * seconds);
        current += remaining * response;
        return current;
    }

    public void target(double value) {
        target = value;
    }

    public double current() {
        return current;
    }

    public double target() {
        return target;
    }

    /** Applies a direct operation immediately and cancels any remaining animation. */
    public void snap(double value) {
        current = value;
        target = value;
    }

    /** Keeps every animation state inside bounds when content or window geometry changes. */
    public void constrain(double minimum, double maximum) {
        double low = Math.min(minimum, maximum);
        double high = Math.max(minimum, maximum);
        current = clamp(current, low, high);
        target = clamp(target, low, high);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
