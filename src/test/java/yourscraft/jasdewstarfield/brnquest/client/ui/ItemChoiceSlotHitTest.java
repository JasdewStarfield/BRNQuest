package yourscraft.jasdewstarfield.brnquest.client.ui;
import yourscraft.jasdewstarfield.brnquest.builtin.client.ItemChoiceScreen;

import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
import static org.junit.jupiter.api.Assertions.*;

class ItemChoiceSlotHitTest {
    @Test void slotBordersAndFractionalMousePositionsBelongToExactlyOneCandidate() {
        var viewport = new UiRect(10, 20, 172, 56);
        for (double x : new double[]{10, 10.9, 11, 26.9, 27, 27.99})
            assertEquals(0, ItemChoiceScreen.candidateIndexAt(viewport, 18, 0, x, 20).orElseThrow());
        assertEquals(1, ItemChoiceScreen.candidateIndexAt(viewport, 18, 0, 28, 20).orElseThrow());
        assertTrue(ItemChoiceScreen.candidateIndexAt(viewport, 18, 0, 172, 20).isEmpty());
        assertTrue(ItemChoiceScreen.candidateIndexAt(viewport, 18, 0, 10, 56).isEmpty());
        assertTrue(ItemChoiceScreen.candidateIndexAt(viewport, 1, 0, 28, 20).isEmpty());
    }

    @Test void partiallyScrolledRowsUseTheRenderedPixelBoundary() {
        var viewport = new UiRect(10, 20, 172, 56);
        // 0.6 rounds to one rendered pixel: the second row starts at y=37, not y=37.4.
        assertEquals(0, ItemChoiceScreen.candidateIndexAt(viewport, 27, .6, 10, 36.99).orElseThrow());
        assertEquals(9, ItemChoiceScreen.candidateIndexAt(viewport, 27, .6, 10, 37).orElseThrow());
        assertTrue(ItemChoiceScreen.candidateIndexAt(viewport, 27, .6, 10, 19.99).isEmpty());
    }
}
