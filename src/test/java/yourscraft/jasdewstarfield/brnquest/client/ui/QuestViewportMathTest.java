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
}
