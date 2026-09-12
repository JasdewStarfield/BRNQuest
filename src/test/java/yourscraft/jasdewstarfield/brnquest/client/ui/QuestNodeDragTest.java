package yourscraft.jasdewstarfield.brnquest.client.ui;

import org.junit.jupiter.api.Test;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.author.DraftBookEditor.Position;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** Test the real gesture coordinator rather than just the snapping math. */
class QuestNodeDragTest {
    private static final ResourceLocation A = ResourceLocation.parse("test:a");
    private static final ResourceLocation B = ResourceLocation.parse("test:b");
    private QuestNodeDrag begin() { return begin(true); }
    private QuestNodeDrag begin(boolean snapToGrid) {
        var drag = new QuestNodeDrag();
        drag.rememberSelection(Set.of(B));
        drag.begin(Map.of(A, new Position(1.25, 0), B, new Position(4.375, 2)), A, 0, 0, 50, 50, 0, snapToGrid);
        return drag;
    }
    @Test void shortPressIsNotAMove() {
        var drag = begin();
        drag.update(0, 0, 50, 50, 100_000_000);
        assertFalse(drag.pickedUp());
        assertEquals(A, drag.anchor());
        assertTrue(drag.releaseMove().isEmpty());
    }
    @Test void earlyMotionBelongsToPanAndPreservesPreviousSelection() {
        var drag = begin();
        assertTrue(drag.requestsPan(60, 50, 100_000_000));
        drag.update(10, 0, 60, 50, 100_000_000);
        assertFalse(drag.pickedUp());
        assertEquals(Set.of(B), drag.previousSelection());
        assertEquals(50, drag.pressX());
        drag.cancel();
        assertFalse(drag.active());
    }
    @Test void stationaryHoldPicksUpButCreatesNoRevision() {
        var drag = begin();
        drag.update(0, 0, 50, 50, 250_000_000);
        assertTrue(drag.pickedUp());
        assertTrue(drag.releaseMove().isEmpty());
    }
    @Test void groupPreviewAndSingleReleaseKeepRelativeOffsets() {
        var drag = begin();
        drag.update(0, 0, 50, 50, 250_000_000);
        drag.update(1.4 * QuestViewportMath.GRID_SCALE, 0, 90, 50, 300_000_000);
        assertEquals(2.65, drag.preview(A).x(), 0.00001);
        assertEquals(3, drag.snapPreview(A).x(), 0.00001);
        var move = drag.releaseMove();
        assertEquals(3.125, move.get(B).x() - move.get(A).x(), 0.00001);
        assertFalse(drag.active());
        assertEquals(move.get(A), drag.preview(A));
        assertTrue(drag.releaseMove().isEmpty());
        assertEquals(move.get(A), drag.preview(A), "Duplicate release must preserve the pending server preview");
    }
    @Test void returnToOriginCancelsMove() {
        var drag = begin();
        drag.update(0, 0, 50, 50, 250_000_000);
        drag.update(60, 0, 110, 50, 300_000_000);
        drag.update(0, 0, 50, 50, 350_000_000);
        assertTrue(drag.releaseMove().isEmpty());
    }
    @Test void rejectedOrConfirmedMoveClearsPreviewWithoutTouchingDefinitions() {
        var drag = begin();
        drag.update(0, 0, 50, 50, 250_000_000);
        drag.update(60, 0, 110, 50, 300_000_000);
        var move = drag.releaseMove();
        drag.reconcile(id -> null, false);
        assertNotNull(drag.preview(A));
        drag.reconcile(move::get, false);
        assertNull(drag.preview(A));
    }
    @Test void freeMovementUsesTheSameBoundedPreviewAndReleaseForEverySelectedNode() {
        var drag = begin(false);
        drag.update(1.123456 * QuestViewportMath.GRID_SCALE, -0.234567 * QuestViewportMath.GRID_SCALE,
                90, 40, 300_000_000);
        var expected = new Position(2.373, -0.235);
        assertEquals(expected, drag.preview(A));
        assertEquals(expected, drag.snapPreview(A));
        var move = drag.releaseMove();
        assertEquals(expected, move.get(A));
        assertEquals(3.125, move.get(B).x() - move.get(A).x(), 0.000001);
        assertEquals(2, move.get(B).y() - move.get(A).y(), 0.000001);
        assertTrue(drag.releaseMove().isEmpty());
        drag.reconcile(id -> null, true);
        assertNull(drag.preview(A), "Rejected free moves must clear the pending preview too");
    }
    @Test void freeMovementCanReturnToOriginOrBeCancelled() {
        var drag = begin(false);
        drag.update(20, -10, 70, 40, 300_000_000);
        drag.update(0, 0, 50, 50, 350_000_000);
        assertTrue(drag.releaseMove().isEmpty());
        drag = begin(false);
        drag.update(20, -10, 70, 40, 300_000_000);
        drag.cancel();
        assertTrue(drag.releaseMove().isEmpty());
        assertNull(drag.preview(A));
    }
}
