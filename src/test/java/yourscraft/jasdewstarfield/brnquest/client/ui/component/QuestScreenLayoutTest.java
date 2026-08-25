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
        assertEquals(new UiRect(0, 20, 1920, 1060), expanded.content());
    }

    @Test void sidePanelsOnlyChangeTheMiddleCanvasBounds() {
        QuestScreenLayout expanded = new QuestScreenLayout(1920, 1080, false, false);
        QuestScreenLayout collapsedWithDetails = new QuestScreenLayout(1920, 1080, true, true);

        assertEquals(146, expanded.canvasLeft());
        assertEquals(10, collapsedWithDetails.canvasLeft());
        assertEquals(1696, collapsedWithDetails.canvasRight());
        assertEquals(1696, collapsedWithDetails.detailLeft());
    }

    @Test void contentHitTestingExcludesBothToolbars() {
        QuestScreenLayout layout = new QuestScreenLayout(1280, 720, false, false);

        assertFalse(layout.isContentY(19.99));
        assertTrue(layout.isContentY(20));
        assertTrue(layout.isContentY(699.99));
        assertFalse(layout.isContentY(700));
    }

    @Test void compactProfilesPreserveUsefulCanvasAtTargetGuiSizes() {
        QuestScreenLayout scaleThree720p = new QuestScreenLayout(427, 240, false, true);
        QuestScreenLayout scaleTwo720p = new QuestScreenLayout(640, 360, false, true);
        QuestScreenLayout scaleTwo1080p = new QuestScreenLayout(960, 540, false, true);

        assertTrue(scaleThree720p.compact());
        assertEquals(100, scaleThree720p.navigationWidth());
        assertEquals(184, scaleThree720p.detailsWidth());
        assertEquals(129, scaleThree720p.canvasRight() - scaleThree720p.canvasLeft());

        assertTrue(scaleTwo720p.compact());
        assertEquals(124, scaleTwo720p.navigationWidth());
        assertEquals(216, scaleTwo720p.detailsWidth());
        assertEquals(286, scaleTwo720p.canvasRight() - scaleTwo720p.canvasLeft());

        assertFalse(scaleTwo1080p.compact());
        assertEquals(590, scaleTwo1080p.canvasRight() - scaleTwo1080p.canvasLeft());
    }

    @Test void responsiveRegionsNeverOverlapAtTheSupportedSmallestTarget() {
        QuestScreenLayout layout = new QuestScreenLayout(427, 240, false, true);

        assertTrue(layout.canvasLeft() < layout.canvasRight());
        assertEquals(layout.canvasRight(), layout.detailLeft());
        assertTrue(layout.topToolbar().bottom() <= layout.content().top());
        assertTrue(layout.content().bottom() <= layout.bottomToolbar().top());
    }
}
