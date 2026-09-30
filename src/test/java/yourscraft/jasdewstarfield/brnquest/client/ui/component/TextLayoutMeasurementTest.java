package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Regression cases distinguish source overflow, successful fitting, clipping, and unknown layout slots. */
class TextLayoutMeasurementTest {
    private static TextLayoutMeasurement measure(UiRect drawn, UiRect available, double width, double height,
                                                  boolean truncated, boolean known) {
        return new TextLayoutMeasurement("Save changes", "test", drawn, available,
                width, height, drawn.width(), drawn.height(), 1, 1, truncated, false, known);
    }

    @Test void fittedTextStillReportsItsOriginalOverflow() {
        var value = measure(new UiRect(10, 10, 70, 19), new UiRect(10, 10, 70, 30), 72, 9, false, true);
        assertEquals(12, value.widthOverflow());
        assertEquals(120, value.occupancyPercent());
        assertTrue(value.overflow());
        assertFalse(value.clipped());
    }

    @Test void unknownControlWidthDoesNotClaimAValidFitOrOverflow() {
        var value = measure(new UiRect(80, 10, 130, 19), new UiRect(0, 0, 100, 50), 50, 9, false, false);
        assertFalse(value.knownSlot());
        assertFalse(value.overflow());
        assertTrue(value.clipped());
        assertTrue(value.hovered(90, 15));
        assertFalse(value.hovered(110, 15));
        assertFalse(value.hovered(20, 15));
    }

    @Test void clippedRowsCannotReceiveHoverOutsideTheirVisibleSlot() {
        var value = measure(new UiRect(10, 0, 80, 9), new UiRect(10, 5, 80, 20), 70, 9, false, true);
        assertTrue(value.clipped());
        assertFalse(value.hovered(30, 2));
        assertTrue(value.hovered(30, 6));
        assertFalse(value.hovered(80, 6));
    }

    @Test void verticalOverflowIsMeasuredIndependently() {
        var value = measure(new UiRect(10, 10, 40, 37), new UiRect(10, 10, 70, 28), 30, 27, false, true);
        assertEquals(0, value.widthOverflow());
        assertEquals(9, value.heightOverflow());
        assertTrue(value.overflow());
    }

    @Test void zeroSpaceAndTruncationRemainObservable() {
        var value = measure(new UiRect(10, 10, 10, 19), new UiRect(10, 10, 10, 19), 72, 9, true, true);
        assertEquals(72, value.widthOverflow());
        assertEquals(Double.POSITIVE_INFINITY, value.occupancyPercent());
        assertTrue(value.truncated());
        assertTrue(value.overflow());
        assertFalse(value.hovered(10, 15));
    }

    @Test void secondaryTextEmptySlotCannotStealThePrimaryLabel() {
        var slot = new UiRect(0, 0, 100, 20);
        var primary = measure(new UiRect(10, 5, 60, 14), slot, 50, 9, false, true);
        var secondary = measure(new UiRect(80, 5, 85, 14), slot, 5, 9, false, true);
        assertSame(primary, TextLayoutMeasurement.pick(java.util.List.of(primary, secondary), 30, 10));
        assertSame(secondary, TextLayoutMeasurement.pick(java.util.List.of(primary, secondary), 82, 10));
    }

    @Test void overlappingPaintUsesTheFinalDrawOrder() {
        var rect = new UiRect(10, 5, 60, 14);
        var behind = measure(rect, rect, 50, 9, false, true);
        var front = measure(rect, rect, 50, 9, false, true);
        assertSame(front, TextLayoutMeasurement.pick(java.util.List.of(behind, front), 30, 10));
        assertNull(TextLayoutMeasurement.pick(java.util.List.of(behind, front), 30, 30));
    }
}
