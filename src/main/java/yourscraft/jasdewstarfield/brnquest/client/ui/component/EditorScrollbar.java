package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.gui.GuiGraphics;

/** Shared scrollbar geometry for navigation, details and future editor lists. */
public final class EditorScrollbar {
    private static final int MINIMUM_THUMB_HEIGHT = 16;

    /** Exact rendered thumb geometry shared by painting and pointer capture. */
    public record Thumb(int top, int bottom) {
        public int height() { return bottom - top; }
        public boolean contains(double y) { return y >= top && y < bottom; }
    }

    private EditorScrollbar() {}

    public static void render(GuiGraphics graphics, int x, int top, int bottom,
                              int contentHeight, int viewportHeight, double scroll) {
        if (contentHeight <= viewportHeight) return;
        Thumb thumb = thumb(top, bottom, contentHeight, viewportHeight, scroll);
        graphics.fill(x, top, x + 3, bottom, GraystonePalette.TRACK);
        graphics.fill(x, thumb.top(), x + 3, thumb.bottom(), GraystonePalette.THUMB);
    }

    public static Thumb thumb(int top, int bottom, int contentHeight, int viewportHeight, double scroll) {
        int trackHeight = Math.max(0, bottom - top);
        int thumbHeight = thumbHeight(trackHeight, contentHeight, viewportHeight);
        int travel = Math.max(0, trackHeight - thumbHeight);
        int maxScroll = Math.max(0, contentHeight - viewportHeight);
        double ratio = maxScroll == 0 ? 0.0 : Math.max(0.0, Math.min(1.0, scroll / maxScroll));
        int thumbTop = top + (int) Math.round(travel * ratio);
        return new Thumb(thumbTop, thumbTop + thumbHeight);
    }

    public static double scrollFromTrack(double mouseY, int top, int bottom,
                                         int contentHeight, int viewportHeight) {
        // Map the pointer to the rendered thumb center so the reachable track ends select exact endpoints.
        int trackHeight = Math.max(0, bottom - top);
        int thumbHeight = thumbHeight(trackHeight, contentHeight, viewportHeight);
        double ratio = Math.max(0.0, Math.min(1.0,
                (mouseY - top - thumbHeight / 2.0) / Math.max(1.0, trackHeight - thumbHeight)));
        return ratio * Math.max(0, contentHeight - viewportHeight);
    }

    /** Preserves the pointer's grab offset instead of snapping the thumb center under the cursor. */
    public static double scrollFromThumb(double mouseY, double grabOffset, int top, int bottom,
                                         int contentHeight, int viewportHeight) {
        int trackHeight = Math.max(0, bottom - top);
        int thumbHeight = thumbHeight(trackHeight, contentHeight, viewportHeight);
        int travel = Math.max(0, trackHeight - thumbHeight);
        if (travel == 0) return 0.0;
        double thumbTop = Math.max(top, Math.min(bottom - thumbHeight, mouseY - grabOffset));
        return (thumbTop - top) / travel * Math.max(0, contentHeight - viewportHeight);
    }
    private static int thumbHeight(int trackHeight, int contentHeight, int viewportHeight) {
        // Even a tiny clipped viewport must keep the thumb inside its track.
        return Math.min(trackHeight, Math.max(MINIMUM_THUMB_HEIGHT,
                (int) Math.round(trackHeight * (viewportHeight / (double) Math.max(1, contentHeight)))));
    }
}
