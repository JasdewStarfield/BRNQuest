package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.gui.GuiGraphics;

/** Shared scrollbar geometry for navigation, details and future editor lists. */
public final class EditorScrollbar {
    private static final int MINIMUM_THUMB_HEIGHT = 16;

    private EditorScrollbar() {}

    public static void render(GuiGraphics graphics, int x, int top, int bottom,
                              int contentHeight, int viewportHeight, double scroll) {
        if (contentHeight <= viewportHeight) return;
        int trackHeight = Math.max(0, bottom - top);
        int thumbHeight = thumbHeight(trackHeight, contentHeight, viewportHeight);
        int travel = trackHeight - thumbHeight;
        int maxScroll = contentHeight - viewportHeight;
        int thumbTop = top + (int) Math.round(travel * (scroll / maxScroll));
        graphics.fill(x, top, x + 3, bottom, GraystonePalette.TRACK);
        graphics.fill(x, thumbTop, x + 3, thumbTop + thumbHeight, GraystonePalette.THUMB);
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
    private static int thumbHeight(int trackHeight, int contentHeight, int viewportHeight) {
        // Even a tiny clipped viewport must keep the thumb inside its track.
        return Math.min(trackHeight, Math.max(MINIMUM_THUMB_HEIGHT,
                (int) Math.round(trackHeight * (viewportHeight / (double) Math.max(1, contentHeight)))));
    }
}
