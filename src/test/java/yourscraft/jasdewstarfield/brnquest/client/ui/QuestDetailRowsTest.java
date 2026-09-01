package yourscraft.jasdewstarfield.brnquest.client.ui;

import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;

import static org.junit.jupiter.api.Assertions.*;

/** Guards detail-row input geometry at both exclusive clipping edges. */
class QuestDetailRowsTest {
    private static final UiRect VIEWPORT = new UiRect(20, 30, 220, 100);

    @Test void partiallyVisibleRowsExposeOnlyTheirVisiblePixels() {
        assertEquals(new UiRect(20, 30, 180, 42),
                QuestDetailRows.visiblePart(new UiRect(20, 18, 180, 42), VIEWPORT));
        assertEquals(new UiRect(20, 92, 180, 100),
                QuestDetailRows.visiblePart(new UiRect(20, 92, 180, 116), VIEWPORT));

        UiRect bottom = QuestDetailRows.visiblePart(new UiRect(20, 92, 180, 116), VIEWPORT);
        assertNotNull(bottom);
        assertTrue(bottom.containsExclusive(40, 99.99));
        assertFalse(bottom.containsExclusive(40, 100));
    }

    @Test void fullyClippedRowsDoNotLeaveGhostTargets() {
        assertNull(QuestDetailRows.visiblePart(new UiRect(20, 6, 180, 30), VIEWPORT));
        assertNull(QuestDetailRows.visiblePart(new UiRect(20, 100, 180, 124), VIEWPORT));
        assertNull(QuestDetailRows.visiblePart(null, VIEWPORT));
    }
}
