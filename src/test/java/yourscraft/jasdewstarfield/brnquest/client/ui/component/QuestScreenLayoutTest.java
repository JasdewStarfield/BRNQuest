package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestScreenLayoutTest {
    @Test void toolbarsRemainFixedWhenSidePanelsChange() {
        QuestScreenLayout expanded = new QuestScreenLayout(1920, 1080, false, false);
        QuestScreenLayout drawersOpen = new QuestScreenLayout(1920, 1080, true, true);

        assertEquals(expanded.topToolbar(), drawersOpen.topToolbar());
        assertEquals(expanded.bottomToolbar(), drawersOpen.bottomToolbar());
        assertEquals(new UiRect(0, 24, 1920, 1056), expanded.content());
    }

    @Test void sidePanelsOnlyChangeTheMiddleCanvasBounds() {
        QuestScreenLayout expanded = new QuestScreenLayout(1920, 1080, false, false);
        QuestScreenLayout collapsedWithDetails = new QuestScreenLayout(1920, 1080, true, true);

        assertEquals(166, expanded.canvasLeft());
        assertEquals(12, collapsedWithDetails.canvasLeft());
        assertEquals(1670, collapsedWithDetails.canvasRight());
        assertEquals(1670, collapsedWithDetails.detailLeft());
    }

    @Test void contentHitTestingExcludesBothToolbars() {
        QuestScreenLayout layout = new QuestScreenLayout(1280, 720, false, false);

        assertFalse(layout.isContentY(23.99));
        assertTrue(layout.isContentY(24));
        assertTrue(layout.isContentY(695.99));
        assertFalse(layout.isContentY(696));
    }
}
