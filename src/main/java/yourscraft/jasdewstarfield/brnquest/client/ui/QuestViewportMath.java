package yourscraft.jasdewstarfield.brnquest.client.ui;

/** Deterministic viewport math kept independent from rendering for unit testing. */
final class QuestViewportMath {
    static final double MIN_ZOOM = 0.50;
    static final double MAX_ZOOM = 2.00;
    static final double GRID_SCALE = 34.0;
    static final int DRAG_DECIMAL_PLACES = 3;
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

    /** Places one graph-pixel coordinate at a requested screen coordinate. */
    static double panForGraphPoint(double graphPixel, double targetScreen, double screenOrigin, double zoom) {
        return targetScreen - screenOrigin - graphPixel * zoom;
    }

    static double clampScroll(double value, int contentHeight, int viewportHeight) {
        return Math.max(0.0, Math.min(Math.max(0, contentHeight - viewportHeight), value));
    }

    /** Returns the nearest visible grid line in quest-coordinate space. */
    static double snapQuestCoordinate(double value) {
        return Math.round(value);
    }

    /** Dragging uses bounded precision while property editing may retain arbitrary finite decimals. */
    static double limitDraggedPrecision(double value) {
        double scale = Math.pow(10.0, DRAG_DECIMAL_PLACES);
        return Math.round(value * scale) / scale;
    }

    /** Snaps the grabbed node and applies the same delta to every node in a multi-selection. */
    static double snappedGroupDelta(double anchorOrigin, double rawDelta) {
        return snapQuestCoordinate(anchorOrigin + rawDelta) - anchorOrigin;
    }

    /** Resolves early pointer travel as canvas panning while preserving a stationary long press. */
    static boolean shouldPanBeforeLongPress(long elapsedNanos, long holdNanos,
                                            double deltaX, double deltaY, double thresholdPixels) {
        return elapsedNanos < holdNanos && Math.hypot(deltaX, deltaY) >= thresholdPixels;
    }

    static int rewardGridHeight(int rewardCount, int columns) {
        if (rewardCount <= 0) return 0;
        return ((rewardCount + Math.max(1, columns) - 1) / Math.max(1, columns)) * REWARD_ROW_HEIGHT;
    }

    static boolean intersectsViewport(double centerX, double centerY, double radius,
                                      double left, double right, double top, double bottom) {
        return centerX + radius >= left && centerX - radius <= right
                && centerY + radius >= top && centerY - radius <= bottom;
    }

    /** Converts the screen-pixel pan into the fixed graph pixel shown at Screen center. */
    static double graphCenterForPan(double pan, double zoom) {
        return -pan / zoom;
    }

    /** Restores a graph-space center without depending on the current window dimensions. */
    static double panForGraphCenter(double graphCenter, double zoom) {
        return -graphCenter * zoom;
    }
}
