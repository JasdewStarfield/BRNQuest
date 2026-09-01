package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EditorListPanelTest {
    private static final UiRect SCREEN = new UiRect(0, 0, 400, 300);
    private static final UiRect BOUNDS = new UiRect(10, 40, 210, 135);

    private EditorListPanel.Frame<Integer> frame(EditorListPanel<Integer> panel, UiRect bounds, int count) {
        return panel.advance(bounds, SCREEN, bounds.right() + 2, 38, 2, count, i -> i, 0.016, 12);
    }

    @Test void halfRowLayoutAndAllHitEdgesMatchClippedPixels() {
        EditorListPanel<Integer> panel = new EditorListPanel<>();
        var frame = frame(panel, BOUNDS, 10);
        assertEquals(3, frame.rows().size());
        assertEquals(new UiRect(10, 116, 210, 135), frame.rows().get(2).visible());
        assertEquals(2, panel.rowAt(20, 134.99).orElseThrow().key());
        assertTrue(panel.rowAt(20, 135).isEmpty());
        assertTrue(panel.rowAt(210, 50).isEmpty());
        assertTrue(panel.rowAt(9, 50).isEmpty());
        assertTrue(panel.rowAt(20, 76).isEmpty(), "The two-pixel row gap is not clickable");
    }

    @Test void emptyZeroHeightAndFullyClippedListsHaveNoInteractions() {
        EditorListPanel<Integer> panel = new EditorListPanel<>();
        assertTrue(frame(panel, BOUNDS, 0).rows().isEmpty());
        assertFalse(panel.mouseClicked(212, 60, 0));
        assertTrue(frame(panel, new UiRect(10, 40, 210, 40), 10).rows().isEmpty());
        assertFalse(panel.mouseScrolled(20, 40, -1, 20));
        assertTrue(frame(panel, BOUNDS.translated(500, 0), 10).rows().isEmpty());
        assertTrue(panel.rowAt(520, 50).isEmpty());
    }

    @Test void wheelChangesTargetButNeverRemapsTheAlreadyRenderedFrame() {
        EditorListPanel<Integer> panel = new EditorListPanel<>();
        frame(panel, BOUNDS, 10);
        assertTrue(panel.mouseScrolled(20, 50, -1, 80));
        assertEquals(0, panel.rowAt(20, 50).orElseThrow().key());
        var animated = frame(panel, BOUNDS, 10);
        assertTrue(animated.pixelScroll() > 0 && animated.pixelScroll() < 80);
        for (var row : animated.rows()) {
            assertEquals(row.key(), panel.rowAt(row.visible().left(), row.visible().top()).orElseThrow().key());
        }
        assertFalse(panel.mouseScrolled(20, 150, -1, 80));
    }

    @Test void roundedVisualOffsetOwnsTheFractionalScrollBoundary() {
        EditorListPanel<Integer> panel = new EditorListPanel<>();
        frame(panel, BOUNDS, 10);
        // Track location maps to 0.6px: visible rows round to one pixel and input must use that same value.
        assertTrue(panel.mouseClicked(212, 40 + 0.6 / 285 * 95, 0));
        var next = panel.advance(BOUNDS, SCREEN, 212, 38, 2, 10, i -> i, 0, 12);
        assertEquals(1, next.pixelScroll());
        assertEquals(1, panel.rowAt(20, 77).orElseThrow().key());
        assertTrue(panel.rowAt(20, 76.99).isEmpty());
    }

    @Test void trackClickUsesExclusiveEdgesAndOnlyAffectsNextFrame() {
        EditorListPanel<Integer> panel = new EditorListPanel<>();
        frame(panel, BOUNDS, 10);
        assertFalse(panel.mouseClicked(215, 80, 0));
        assertFalse(panel.mouseClicked(212, 135, 0));
        assertFalse(panel.mouseClicked(212, 80, 1));
        assertTrue(panel.mouseClicked(212, 100, 0));
        assertEquals(0, panel.rowAt(20, 50).orElseThrow().key());
        assertTrue(frame(panel, BOUNDS, 10).pixelScroll() > 0);
    }

    @Test void resizeAndContentRemovalClampScrollBeforeGeneratingRows() {
        EditorListPanel<Integer> panel = new EditorListPanel<>();
        frame(panel, BOUNDS, 10);
        panel.mouseClicked(212, 134, 0);
        assertTrue(frame(panel, BOUNDS, 10).pixelScroll() > 200);
        var shorter = frame(panel, BOUNDS, 1);
        assertEquals(0, shorter.pixelScroll());
        assertEquals(0, shorter.rows().getFirst().key());
        assertEquals(0, frame(panel, new UiRect(10, 40, 210, 290), 3).pixelScroll());
        panel.reset();
        assertTrue(panel.rowAt(20, 50).isEmpty());
    }

    @Test void onlyVisibleKeysAreRequestedAndScreenOffsetIsAppliedOnce() {
        EditorListPanel<Integer> panel = new EditorListPanel<>();
        AtomicInteger calls = new AtomicInteger();
        UiRect moved = BOUNDS.translated(150, 0);
        var result = panel.advance(moved, new UiRect(0, 0, 300, 300), 362, 38, 2, 4096,
                i -> { calls.incrementAndGet(); return i; }, 0, 12);
        assertEquals(3, calls.get());
        assertEquals(new UiRect(160, 40, 300, 76), result.rows().getFirst().visible());
        assertTrue(panel.rowAt(20, 50).isEmpty());
        assertEquals(0, panel.rowAt(170, 50).orElseThrow().key());
        assertTrue(panel.rowAt(300, 50).isEmpty());
    }

    @Test void invalidMetricsFailAtTheComponentBoundary() {
        EditorListPanel<Integer> panel = new EditorListPanel<>();
        assertThrows(IllegalArgumentException.class,
                () -> panel.advance(BOUNDS, SCREEN, 212, 0, 0, 1, i -> i, 0, 12));
        assertThrows(IllegalArgumentException.class,
                () -> panel.advance(BOUNDS, SCREEN, 212, 38, 38, 1, i -> i, 0, 12));
    }
}
