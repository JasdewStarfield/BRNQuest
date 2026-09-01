package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import java.util.Objects;
import java.util.function.Supplier;

/** Smooth focus independent of a screen/book. Destination is sampled once, not from animated panel edges. */
public final class EditorSelectionFocus<K> {
    public record Point(double x, double y) {}
    private final EditorSmoothValue x = new EditorSmoothValue(0, 0.01);
    private final EditorSmoothValue y = new EditorSmoothValue(0, 0.01);
    private boolean observedOpen;
    private K observedId;
    private K focusing;
    private boolean targetInitialized;

    public boolean observe(boolean open, K selected) {
        boolean requested = open && (!observedOpen || !Objects.equals(observedId, selected));
        observedOpen = open;
        observedId = selected;
        return requested;
    }
    public K focusing() { return focusing; }
    public void start(K id, double currentX, double currentY) {
        cancel(currentX, currentY);
        focusing = id;
    }
    public Point advance(boolean paused, double currentX, double currentY, Supplier<Point> destination,
                         double seconds, double speed) {
        if (focusing == null) return new Point(currentX, currentY);
        if (paused) {
            x.snap(currentX);
            y.snap(currentY);
            return new Point(currentX, currentY);
        }
        if (!targetInitialized) {
            Point target = destination.get();
            x.target(target.x());
            y.target(target.y());
            targetInitialized = true;
        }
        Point point = new Point(x.advanceFrame(seconds, speed), y.advanceFrame(seconds, speed));
        if (x.current() == x.target() && y.current() == y.target()) {
            focusing = null;
            targetInitialized = false;
        }
        return point;
    }
    public void cancel(double currentX, double currentY) {
        focusing = null;
        targetInitialized = false;
        x.snap(currentX);
        y.snap(currentY);
    }
    public void reset(double currentX, double currentY) {
        cancel(currentX, currentY);
        observedOpen = false;
        observedId = null;
    }
}
