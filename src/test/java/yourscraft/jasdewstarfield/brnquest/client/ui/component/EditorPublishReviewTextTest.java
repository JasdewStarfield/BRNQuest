package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class EditorPublishReviewTextTest {
    @Test void stableProtocolEnumsAndPathsMapToReviewedTranslationKeys() {
        assertTranslation("screen.brnquest.editor.publish.review.diff.kind.property_changed",
                EditorPublishReviewText.kind("PROPERTY_CHANGED"));
        assertTranslation("screen.brnquest.editor.publish.review.diff.object.quest",
                EditorPublishReviewText.objectKind("QUEST"));
        assertTranslation("screen.brnquest.editor.publish.review.diff.path.chapter",
                EditorPublishReviewText.path("chapter_id"));
        assertTranslation("screen.brnquest.editor.publish.review.diff.path.config",
                EditorPublishReviewText.path("config.item"));
    }

    @Test void changedValuesUseSeparateTooltipLines() {
        var change = new EditorPublishReviewModel.Change("PROPERTY_CHANGED", "QUEST", "test:quest",
                "title", "Before", "After");

        assertEquals(2, EditorPublishReviewText.valueTooltip(change).size());
        assertTranslation("screen.brnquest.editor.publish.review.diff.value.before",
                EditorPublishReviewText.valueTooltip(change).getFirst());
        assertTranslation("screen.brnquest.editor.publish.review.diff.value.after",
                EditorPublishReviewText.valueTooltip(change).getLast());
    }

    private static void assertTranslation(String expected, Component component) {
        TranslatableContents contents = assertInstanceOf(TranslatableContents.class, component.getContents());
        assertEquals(expected, contents.getKey());
    }
    @Test void compactSummaryRetainsTitlesAndAcceptsOldScalarPayloads() {
        assertEquals("Named reward", EditorPublishReviewText.storedTitle("{\"config\":{\"title\":\"Named reward\"}}"));
        assertEquals("Named reward", EditorPublishReviewText.compactValue("{\"config\":{\"title\":\"Named reward\"}}"));
        assertEquals("old scalar", EditorPublishReviewText.compactValue("old scalar"));
        assertEquals("", EditorPublishReviewText.storedTitle("LegacyRecord[id=test:q]"));
        assertTrue(EditorPublishReviewText.compactValue("x".repeat(200)).length() <= 100);
    }

}
