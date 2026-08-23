package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorPublishReviewPanelTest {
    @Test void reviewPanelStaysInsideTheContentAreaAtSmallAndLargeSizes() {
        for (QuestScreenLayout screen : new QuestScreenLayout[] {
                new QuestScreenLayout(360, 240, false, false),
                new QuestScreenLayout(1920, 1080, true, true)}) {
            var layout = EditorPublishReviewPanel.layout(screen);
            assertTrue(layout.panel().left() >= 0);
            assertTrue(layout.panel().right() <= screen.width());
            assertTrue(layout.panel().top() >= screen.content().top());
            assertTrue(layout.panel().bottom() <= screen.content().bottom());
            assertTrue(layout.list().height() >= EditorPublishReviewPanel.ROW_HEIGHT);
        }
    }

    @Test void rowHitTestingUsesTheSameScrollWindowAsRendering() {
        var layout = EditorPublishReviewPanel.layout(new QuestScreenLayout(800, 600, false, false));
        assertEquals(7, EditorPublishReviewPanel.rowAt(layout, 7, 30,
                layout.list().left() + 4, layout.list().top() + 4));
        assertEquals(-1, EditorPublishReviewPanel.rowAt(layout, 0, 0,
                layout.list().left() + 4, layout.list().top() + 4));
        assertTrue(EditorPublishReviewPanel.maximumScroll(layout, 30) > 0);
    }
}
