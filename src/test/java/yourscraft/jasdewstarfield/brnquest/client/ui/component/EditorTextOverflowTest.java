package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import org.junit.jupiter.api.Test;
import java.util.function.ToIntFunction;
import static org.junit.jupiter.api.Assertions.*;

/** Real overflow scenarios: short actions, namespaced IDs, tiny slots and supplementary Unicode glyphs. */
class EditorTextOverflowTest {
    private final ToIntFunction<String> width = text -> text.codePointCount(0, text.length()) * 6;

    @Test void fullActionRemainsUntouchedWhenItFits() {
        assertEquals("Add Chapter", EditorTextLayout.ellipsize("Add Chapter", 66, width, false));
    }
    @Test void actionOmissionIsVisibleAndStaysInsideTheSlot() {
        assertEquals("Prope…", EditorTextLayout.ellipsize("Properties", 36, width, false));
        assertEquals("", EditorTextLayout.ellipsize("Properties", 5, width, false));
    }
    @Test void identifierRetainsItsDistinguishingSuffixAndTheStoredValue() {
        String id = "minecraft:recipes/food/cooked_beef";
        String visible = EditorTextLayout.ellipsize(id, 90, width, true);
        assertTrue(visible.contains("…"));
        assertTrue(visible.startsWith("minec"));
        assertTrue(visible.endsWith("beef"));
        assertTrue(width.applyAsInt(visible) <= 90);
        assertEquals("minecraft:recipes/food/cooked_beef", id);
    }
    @Test void omittedUnicodeTextNeverSplitsASurrogatePair() {
        String source = "任务😀😀😀😀😀😀😀😀尾部";
        String visible = EditorTextLayout.ellipsize(source, 36, width, true);
        assertTrue(width.applyAsInt(visible) <= 36);
        assertTrue(visible.endsWith("尾部"));
        assertEquals(visible, new String(visible.getBytes(java.nio.charset.StandardCharsets.UTF_8), java.nio.charset.StandardCharsets.UTF_8));
    }
    @Test void ordinaryLabelsAreNotClassifiedAsIdentifiers() {
        assertFalse(EditorTextLayout.identifier("Crafting only"));
        assertFalse(EditorTextLayout.identifier("Error: failed"));
        assertTrue(EditorTextLayout.identifier("minecraft:recipes/food/cooked_beef"));
        assertTrue(EditorTextLayout.identifier("#pack:quests/group"));
    }
    @Test void narrowEditorActionsHaveTwoDistinctRowsWithRoomForTheirLabels() {
        var layout = new QuestScreenLayout(640, 374, false, true);
        var first = layout.detailEditorButton(0);
        var second = layout.detailEditorButton(1);
        var third = layout.detailEditorButton(2);
        var fourth = layout.detailEditorButton(3);
        assertTrue(first.width() >= 80);
        assertEquals(first.top(), second.top());
        assertEquals(third.top(), fourth.top());
        assertTrue(first.bottom() < third.top());
        assertTrue(first.right() < second.left());
        assertEquals(first.left(), third.left());
        assertTrue(fourth.bottom() < layout.bottomToolbar().top());
    }
}
