package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorListViewportTest {
    @Test void reservesAHalfRowPreviewWithoutExceedingAvailableSpace() {
        EditorListViewport viewport = EditorListViewport.withHalfRowPreview(40, 260, 38);

        assertEquals(209, viewport.height());
        assertEquals(5 * 38 + 19, viewport.height());
        assertTrue(viewport.bottom() <= 260);
    }

    @Test void lowerEdgeIsExcludedFromPointerAndRowVisibility() {
        EditorListViewport viewport = new EditorListViewport(40, 100);

        assertTrue(viewport.containsY(99.99));
        assertFalse(viewport.containsY(100));
        assertTrue(viewport.intersects(new UiRect(0, 90, 20, 110)));
        assertFalse(viewport.intersects(new UiRect(0, 100, 20, 120)));
    }
}
