package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Camera regression gates use explicit frame time instead of client ticks or sleeps. */
class EditorSelectionFocusTest {
    @Test void sameIdModeSwitchDoesNotRestartFocus() {
        var focus = new EditorSelectionFocus<String>();
        assertTrue(focus.observe(true, "a"));
        assertFalse(focus.observe(true, "a"));
        assertTrue(focus.observe(true, "b"));
        assertFalse(focus.observe(false, "b"));
        assertTrue(focus.observe(true, "b"));
    }
    @Test void finalDestinationIsSampledOnceDespiteChangingDrawerBounds() {
        var focus = new EditorSelectionFocus<String>();
        var calls = new AtomicInteger();
        focus.start("a", 0, 0);
        EditorSelectionFocus.Point point = null;
        for (int i = 0; i < 240; i++) {
            point = focus.advance(false, point == null ? 0 : point.x(), point == null ? 0 : point.y(),
                    () -> new EditorSelectionFocus.Point(calls.incrementAndGet() * 100, 50), 1.0 / 60, 12);
        }
        assertEquals(1, calls.get());
        assertEquals(100, point.x(), 0.0001);
        assertEquals(50, point.y(), 0.0001);
        assertNull(focus.focusing());
    }
    @Test void heldGestureDefersDestinationAndKeepsDrawnCamera() {
        var focus = new EditorSelectionFocus<String>();
        focus.start("a", 20, 30);
        var point = focus.advance(true, 20, 30, () -> { fail("Held pointer cannot move camera"); return null; }, .02, 12);
        assertEquals(new EditorSelectionFocus.Point(20, 30), point);
        assertEquals("a", focus.focusing());
    }
    @Test void cancellationDoesNotRearmTheSameSelection() {
        var focus = new EditorSelectionFocus<String>();
        focus.observe(true, "a");
        focus.start("a", 0, 0);
        focus.cancel(12, 34);
        assertFalse(focus.observe(true, "a"));
        assertNull(focus.focusing());
    }
    @Test void bookResetAllowsSameIdToFocusAgain() {
        var focus = new EditorSelectionFocus<String>();
        focus.observe(true, "a");
        focus.reset(0, 0);
        assertTrue(focus.observe(true, "a"));
    }
}
