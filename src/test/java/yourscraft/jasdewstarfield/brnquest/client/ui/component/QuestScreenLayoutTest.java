package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestScreenLayoutTest {
    @Test void acceptanceSizesKeepCanvasAndToolbarsInsideTheScreen() {
        // Exercise GUI-scaled dimensions and both drawers, including the smallest acceptance sample.
        for (int[] size : new int[][]{{320, 240}, {480, 270}, {640, 360}, {960, 540}}) {
            for (boolean collapsed : new boolean[]{false, true}) {
                for (boolean details : new boolean[]{false, true}) {
                    var layout = new QuestScreenLayout(size[0], size[1], collapsed, details);
                    assertTrue(layout.canvasLeft() >= 0);
                    assertTrue(layout.canvasLeft() < layout.canvasRight());
                    assertTrue(layout.canvasRight() <= size[0]);
                    assertEquals(layout.topToolbar().bottom(), layout.content().top());
                    assertEquals(layout.content().bottom(), layout.bottomToolbar().top());
                    assertEquals(size[1], layout.bottomToolbar().bottom());
                }
            }
        }
    }

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

    @Test void animatedDrawerBoundsStayBetweenTheirClosedAndOpenEndpoints() {
        QuestScreenLayout layout = new QuestScreenLayout(1920, 1080, false, true);

        assertEquals(10, layout.canvasLeft(0));
        assertEquals(78, layout.canvasLeft(0.5));
        assertEquals(146, layout.canvasLeft(1));
        assertEquals(1920, layout.canvasRight(0));
        assertEquals(1808, layout.canvasRight(0.5));
        assertEquals(1696, layout.canvasRight(1));
        assertEquals(-136, layout.navigationDrawerOffset(0));
        assertEquals(-68, layout.navigationDrawerOffset(0.5));
        assertEquals(0, layout.navigationDrawerOffset(1));
        assertEquals(224, layout.detailsDrawerOffset(0));
        assertEquals(112, layout.detailsDrawerOffset(0.5));
        assertEquals(0, layout.detailsDrawerOffset(1));
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
