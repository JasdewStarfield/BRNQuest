package yourscraft.jasdewstarfield.brnquest.client.ui;

/** Deterministic viewport math kept independent from rendering for unit testing. */
final class QuestViewportMath {
    static final double MIN_ZOOM = 0.50;
    static final double MAX_ZOOM = 2.00;
    static final double GRID_SCALE = 34.0;
    static final int REWARD_ROW_HEIGHT = 28;

    private QuestViewportMath() {}

    static double clampZoom(double value) {
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, value));
    }

    static double panForStableAnchor(double anchorScreen, double screenOrigin, double oldPan,
                                     double oldZoom, double newZoom) {
        double worldAtAnchor = (anchorScreen - screenOrigin - oldPan) / (GRID_SCALE * oldZoom);
        return anchorScreen - screenOrigin - worldAtAnchor * GRID_SCALE * newZoom;
    }

    static double clampScroll(double value, int contentHeight, int viewportHeight) {
        return Math.max(0.0, Math.min(Math.max(0, contentHeight - viewportHeight), value));
    }

    static int rewardGridHeight(int rewardCount, int columns) {
        if (rewardCount <= 0) return 0;
        return ((rewardCount + Math.max(1, columns) - 1) / Math.max(1, columns)) * REWARD_ROW_HEIGHT;
    }

    static boolean intersectsViewport(int centerX, int centerY, int radius,
                                      int left, int right, int top, int bottom) {
        return centerX + radius >= left && centerX - radius <= right
                && centerY + radius >= top && centerY - radius <= bottom;
    }

    static double panForStableOrigin(double pan, int oldOrigin, int newOrigin) {
        return pan + oldOrigin - newOrigin;
    }
}
