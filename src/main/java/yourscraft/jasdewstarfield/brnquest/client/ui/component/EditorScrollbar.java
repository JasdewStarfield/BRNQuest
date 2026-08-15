package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.gui.GuiGraphics;

/** Shared scrollbar geometry for navigation, details and future editor lists. */
public final class EditorScrollbar {
    private static final int MINIMUM_THUMB_HEIGHT = 16;

    private EditorScrollbar() {}

    public static void render(GuiGraphics graphics, int x, int top, int bottom,
                              int contentHeight, int viewportHeight, double scroll) {
        if (contentHeight <= viewportHeight) return;
        int trackHeight = bottom - top;
        int thumbHeight = Math.max(MINIMUM_THUMB_HEIGHT,
                (int) Math.round(trackHeight * (viewportHeight / (double) contentHeight)));
        int travel = trackHeight - thumbHeight;
        int maxScroll = contentHeight - viewportHeight;
        int thumbTop = top + (int) Math.round(travel * (scroll / maxScroll));
        graphics.fill(x, top, x + 3, bottom, 0x66343D49);
        graphics.fill(x, thumbTop, x + 3, thumbTop + thumbHeight, 0xFF7C8CA0);
    }

    public static double scrollFromTrack(double mouseY, int top, int bottom,
                                         int contentHeight, int viewportHeight) {
        double ratio = Math.max(0.0, Math.min(1.0, (mouseY - top) / Math.max(1.0, bottom - top)));
        return ratio * Math.max(0, contentHeight - viewportHeight);
    }
}
