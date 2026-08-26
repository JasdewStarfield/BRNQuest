package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Owns one pixel scroll position and its matching scrollbar rendering.
 * Callers use the returned visual value for both row placement and hit testing.
 */
public final class EditorSmoothScroll {
    private final EditorSmoothValue motion = new EditorSmoothValue(0.0);
    private double visual;

    /** Advances and renders the matching scrollbar in one shared operation. */
    public double frameAndRender(GuiGraphics graphics, int x, int top, int bottom,
                                 int contentHeight, int viewportHeight, double elapsedSeconds,
                                 double smoothSpeed) {
        constrain(contentHeight, viewportHeight);
        visual = motion.advanceFrame(elapsedSeconds, smoothSpeed);
        EditorScrollbar.render(graphics, x, top, bottom, contentHeight, viewportHeight, visual);
        return visual;
    }

    /** Adds a configurable pixel wheel step while retaining the current animation target. */
    public void scrollWheel(double wheelDelta, double scrollStep, int contentHeight, int viewportHeight) {
        double maximum = maximum(contentHeight, viewportHeight);
        motion.target(clamp(motion.target() - wheelDelta * scrollStep, 0.0, maximum));
    }

    /** Immediately follows a scrollbar track click without residual easing. */
    public void snapFromTrack(double mouseY, int top, int bottom, int contentHeight, int viewportHeight) {
        snap(EditorScrollbar.scrollFromTrack(mouseY, top, bottom, contentHeight, viewportHeight));
    }

    /** Handles the shared three-pixel scrollbar track without exposing geometry to each screen. */
    public boolean handleTrackClick(double mouseX, double mouseY, int x, int top, int bottom,
                                    int contentHeight, int viewportHeight) {
        if (contentHeight <= viewportHeight || mouseX < x || mouseX > x + 3
                || mouseY < top || mouseY > bottom) return false;
        snapFromTrack(mouseY, top, bottom, contentHeight, viewportHeight);
        return true;
    }

    public void constrain(int contentHeight, int viewportHeight) {
        motion.constrain(0.0, maximum(contentHeight, viewportHeight));
        visual = clamp(visual, 0.0, maximum(contentHeight, viewportHeight));
    }

    public void snap(double value) {
        motion.snap(value);
        visual = value;
    }

    public double visual() {
        return visual;
    }

    public double target() {
        return motion.target();
    }

    public int firstIndex(int rowHeight) {
        return Math.max(0, (int) Math.floor(visual / Math.max(1, rowHeight)));
    }

    public int rowOffset(int rowHeight) {
        int height = Math.max(1, rowHeight);
        return -(int) Math.round(visual - firstIndex(height) * (double) height);
    }

    /** Maps a visible pointer position through the same pixel offset used during rendering. */
    public int rowAt(double mouseY, int rowsTop, int rowsBottom, int rowHeight, int itemCount) {
        if (mouseY < rowsTop || mouseY >= rowsBottom) return -1;
        int index = (int) Math.floor((mouseY - rowsTop + visual) / Math.max(1, rowHeight));
        return index >= 0 && index < itemCount ? index : -1;
    }

    private static double maximum(int contentHeight, int viewportHeight) {
        return Math.max(0.0, contentHeight - viewportHeight);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
