package yourscraft.jasdewstarfield.brnquest.client.ui;

import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
import yourscraft.jasdewstarfield.brnquest.client.ui.document.DocumentLayout;
import yourscraft.jasdewstarfield.brnquest.data.text.RichDocument;

import java.net.URI;
import java.util.List;

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

    @Test void documentLinksExposeOnlyTheirVisibleIntersection() {
        RichDocument.LinkDestination destination = new RichDocument.ExternalLink(
                URI.create("https://example.test"));
        DocumentLayout document = new DocumentLayout(List.of(), List.of(
                new DocumentLayout.LinkHit(destination, new DocumentLayout.Bounds(0, 0, 20, 10)),
                new DocumentLayout.LinkHit(destination, new DocumentLayout.Bounds(0, 30, 20, 40))), 40, 20);

        List<QuestDetailsInteraction.LinkTarget> links = QuestDetailsPanel.visibleDocumentLinks(
                document, 20, 25, new UiRect(25, 30, 40, 60));

        assertEquals(List.of(
                new QuestDetailsInteraction.LinkTarget(destination, new UiRect(25, 30, 40, 35)),
                new QuestDetailsInteraction.LinkTarget(destination, new UiRect(25, 55, 40, 60))), links);
    }
}
