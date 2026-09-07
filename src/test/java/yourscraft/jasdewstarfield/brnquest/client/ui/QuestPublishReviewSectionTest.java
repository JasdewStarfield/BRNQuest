package yourscraft.jasdewstarfield.brnquest.client.ui;

import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPublishReviewPanel;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPublishReviewModel;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.QuestScreenLayout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies that the composed review overlay emits intents without invoking editor state or networking. */
class QuestPublishReviewSectionTest {
    private final QuestScreenLayout screen = new QuestScreenLayout(900, 600, false, false);

    @Test void confirmCarriesTheExactReviewedRevision() {
        QuestPublishReviewSection section = new QuestPublishReviewSection();
        section.open(review(true));
        var confirm = EditorPublishReviewPanel.layout(screen).confirm();

        QuestPublishReviewSection.ClickResult result = section.click(
                screen, confirm.centerX(), confirm.centerY(), 0);

        assertTrue(result.consumed());
        assertEquals(QuestPublishReviewSection.Action.CONFIRM, result.intent().action());
        assertEquals("draft-r2", result.intent().reviewedRevision());
    }

    @Test void blockedReviewConsumesConfirmWithoutPublishing() {
        QuestPublishReviewSection section = new QuestPublishReviewSection();
        section.open(review(false));
        var confirm = EditorPublishReviewPanel.layout(screen).confirm();

        QuestPublishReviewSection.ClickResult result = section.click(
                screen, confirm.centerX(), confirm.centerY(), 0);

        assertTrue(result.consumed());
        assertNull(result.intent());
    }

    @Test void diagnosticRowReturnsAParsedObjectJumpAndCloseClearsPayload() {
        QuestPublishReviewSection section = new QuestPublishReviewSection();
        section.open(review(false));
        var list = EditorPublishReviewPanel.layout(screen).list();

        QuestPublishReviewSection.ClickResult result = section.click(
                screen, list.left() + 4, list.top() + 4, 0);

        assertEquals(QuestPublishReviewSection.Action.JUMP_TO_OBJECT, result.intent().action());
        assertEquals("brnquest:test_quest", result.intent().objectId().toString());
        assertTrue(section.active());
        section.close();
        assertFalse(section.active());
    }

    private static EditorPublishReviewModel review(boolean allowed) {
        var diagnostic = new EditorPublishReviewModel.Diagnostic(
                "ERROR", "TEST", "brnquest:test_quest", "title", "Test diagnostic");
        return new EditorPublishReviewModel(allowed, "draft-r1", "draft-r2",
                1, 0, false, List.of(diagnostic), List.of());
    }
}
