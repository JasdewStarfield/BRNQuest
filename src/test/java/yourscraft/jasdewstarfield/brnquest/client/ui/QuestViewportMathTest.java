package yourscraft.jasdewstarfield.brnquest.client.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestViewportMathTest {
    @Test void zoomKeepsWorldCoordinateAtVisualCenterStable() {
        double anchor = 640;
        double origin = 260;
        double oldPan = 85;
        double oldZoom = 1.0;
        double worldBefore = (anchor - origin - oldPan) / (QuestViewportMath.GRID_SCALE * oldZoom);
        double newZoom = 1.5;
        double newPan = QuestViewportMath.panForStableAnchor(anchor, origin, oldPan, oldZoom, newZoom);
        double worldAfter = (anchor - origin - newPan) / (QuestViewportMath.GRID_SCALE * newZoom);

        assertEquals(worldBefore, worldAfter, 0.000001);
    }

    @Test void zoomAndScrollStayInsideSafetyLimits() {
        assertEquals(QuestViewportMath.MIN_ZOOM, QuestViewportMath.clampZoom(-10));
        assertEquals(QuestViewportMath.MAX_ZOOM, QuestViewportMath.clampZoom(10));
        assertEquals(0, QuestViewportMath.clampScroll(-20, 500, 200));
        assertEquals(300, QuestViewportMath.clampScroll(900, 500, 200));
    }

    @Test void selectedNodeCanBeCenteredInsideTheRemainingCanvas() {
        double graphPixel = 170;
        double targetScreen = 600;
        double origin = 480;
        double zoom = 1.5;

        double pan = QuestViewportMath.panForGraphPoint(graphPixel, targetScreen, origin, zoom);

        assertEquals(targetScreen, origin + pan + graphPixel * zoom, 0.000001);
    }

    @Test void draggedAnchorSnapsToGridWhileGroupSpacingKeepsBoundedPrecision() {
        double anchorOrigin = 1.25;
        double siblingOrigin = 4.37549;
        double rawDelta = 1.40;

        double snappedDelta = QuestViewportMath.snappedGroupDelta(anchorOrigin, rawDelta);
        double anchorTarget = QuestViewportMath.limitDraggedPrecision(anchorOrigin + snappedDelta);
        double siblingTarget = QuestViewportMath.limitDraggedPrecision(siblingOrigin + snappedDelta);

        assertEquals(3.0, anchorTarget, 0.000001);
        assertEquals(6.125, siblingTarget, 0.000001);
        assertEquals(3, QuestViewportMath.DRAG_DECIMAL_PLACES);
    }

    @Test void earlyTravelPansCanvasButStationaryHoldAndPickedUpMotionDoNot() {
        long holdNanos = 220_000_000L;

        assertEquals(true, QuestViewportMath.shouldPanBeforeLongPress(
                80_000_000L, holdNanos, 4.0, 0.0, 4.0));
        assertEquals(false, QuestViewportMath.shouldPanBeforeLongPress(
                80_000_000L, holdNanos, 2.0, 0.0, 4.0));
        assertEquals(false, QuestViewportMath.shouldPanBeforeLongPress(
                holdNanos, holdNanos, 20.0, 0.0, 4.0));
    }

    @Test void partialRewardRowStillContributesItsFullHeight() {
        assertEquals(0, QuestViewportMath.rewardGridHeight(0, 8));
        assertEquals(28, QuestViewportMath.rewardGridHeight(1, 8));
        assertEquals(28, QuestViewportMath.rewardGridHeight(8, 8));
        assertEquals(56, QuestViewportMath.rewardGridHeight(9, 8));
    }

    @Test void nodeRemainsVisibleUntilItsWholeBoundsLeaveViewport() {
        assertEquals(true, QuestViewportMath.intersectsViewport(95, 100, 10, 100, 300, 0, 200));
        assertEquals(true, QuestViewportMath.intersectsViewport(305, 100, 10, 100, 300, 0, 200));
        assertEquals(false, QuestViewportMath.intersectsViewport(89, 100, 10, 100, 300, 0, 200));
        assertEquals(false, QuestViewportMath.intersectsViewport(311, 100, 10, 100, 300, 0, 200));
    }

    @Test void cachedGraphCenterRestoresAtAnyZoomWithoutPanelDimensions() {
        double center = 91.25;
        double zoom = 1.7;

        double pan = QuestViewportMath.panForGraphCenter(center, zoom);

        assertEquals(center, QuestViewportMath.graphCenterForPan(pan, zoom), 0.000001);
    }
}
