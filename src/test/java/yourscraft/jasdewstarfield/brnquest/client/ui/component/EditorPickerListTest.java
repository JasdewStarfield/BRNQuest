package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EditorPickerListTest {
    private static final UiRect BOUNDS = new UiRect(10, 10, 210, 152);

    @Test void popupHeightFitsEveryRequestedRowAboveItsFooter() {
        int footer = 24;
        int height = EditorPickerList.popupHeightForRows(8, footer);
        UiRect listBounds = new UiRect(2, 2, 418, height - footer);
        assertEquals(8 * EditorPickerList.ROW_HEIGHT, EditorPickerList.rowsBounds(listBounds).height());
        EditorPickerList<Integer> picker = new EditorPickerList<>();
        var frame = picker.advance(listBounds, listBounds, 12, i -> i, 0, 12);
        assertEquals(8, frame.rows().size(), "the last allocated row must not disappear at the footer");
    }

    @Test void searchFooterAndScrollbarNeverSelectAnEntry() {
        EditorPickerList<String> picker = new EditorPickerList<>();
        picker.advance(BOUNDS, BOUNDS, 8, i -> "entry-" + i, 0, 12);
        assertTrue(picker.entryAt(30, 29).isEmpty());
        assertTrue(picker.entryAt(30, 150).isEmpty());
        assertTrue(picker.entryAt(206, 50).isEmpty());
        assertFalse(picker.mouseScrolled(30, 20, -1, 20));
        assertEquals("entry-0", picker.entryAt(30, 30).orElseThrow());
    }

    @Test void filteringInvalidatesOldRowsBeforeTheNextFrame() {
        EditorPickerList<String> picker = new EditorPickerList<>();
        picker.advance(BOUNDS, BOUNDS, 20, i -> "before-" + i, 0, 12);
        picker.mouseScrolled(30, 50, -1, 300);
        picker.reset();
        assertTrue(picker.entryAt(30, 50).isEmpty());
        assertFalse(picker.mouseClicked(206, 50, 0));
        var frame = picker.advance(BOUNDS, BOUNDS, 1, i -> "filtered", 0.1, 12);
        assertEquals(0, frame.pixelScroll());
        assertEquals("filtered", picker.entryAt(30, 50).orElseThrow());
    }

    @Test void displayedIdentitySurvivesDataReorderingWithoutUsingNewIndices() {
        EditorPickerList<String> picker = new EditorPickerList<>();
        List<String> ids = new ArrayList<>(List.of("alice-uuid", "bob-uuid"));
        picker.advance(BOUNDS, BOUNDS, ids.size(), ids::get, 0, 12);
        java.util.Collections.reverse(ids);
        assertEquals("alice-uuid", picker.entryAt(30, 40).orElseThrow());
        picker.invalidate();
        assertTrue(picker.entryAt(30, 40).isEmpty());
        picker.advance(BOUNDS, BOUNDS, ids.size(), ids::get, 0, 12);
        assertEquals("bob-uuid", picker.entryAt(30, 40).orElseThrow());
    }

    @Test void resizeInvalidatesGeometryButPreservesAndClampsScroll() {
        EditorPickerList<Integer> picker = new EditorPickerList<>();
        picker.advance(BOUNDS, BOUNDS, 20, i -> i, 0, 12);
        picker.mouseClicked(206, 90, 0);
        assertEquals(240, picker.advance(BOUNDS, BOUNDS, 20, i -> i, 0, 12).pixelScroll());
        picker.invalidate();
        assertFalse(picker.mouseScrolled(30, 40, -1, 30));
        assertEquals(240, picker.advance(BOUNDS, BOUNDS, 20, i -> i, 0, 12).pixelScroll());
        assertEquals(0, picker.advance(BOUNDS, BOUNDS, 2, i -> i, 0, 12).pixelScroll());
    }

    @Test void shortPickerUsesPartialRowWithoutCrossingTheFooter() {
        UiRect shortBounds = new UiRect(10, 10, 210, 45);
        assertEquals(new UiRect(12, 30, 204, 43), EditorPickerList.rowsBounds(shortBounds));
        EditorPickerList<Integer> picker = new EditorPickerList<>();
        var frame = picker.advance(shortBounds, shortBounds, 10, i -> i, 0, 12);
        assertEquals(13, frame.viewport().height());
        assertEquals(0, picker.entryAt(30, 42.99).orElseThrow());
        assertTrue(picker.entryAt(30, 43).isEmpty());
        var empty = picker.advance(new UiRect(10, 10, 210, 20), shortBounds, 10, i -> i, 0, 12);
        assertTrue(empty.rows().isEmpty());
    }

    @Test void oversizedPickerFitsScrollExtentSoLastChoiceRemainsReachable() {
        EditorPickerList<Integer> picker = new EditorPickerList<>();
        UiRect screen = new UiRect(0, 0, 220, 90);
        var frame = picker.advance(BOUNDS, screen, 20, i -> i, 0, 12);
        assertEquals(30, frame.viewport().height());
        // A large wheel gesture approaches the final row in the reduced viewport.
        picker.mouseScrolled(30, 40, -100, 30);
        frame = picker.advance(BOUNDS, screen, 20, i -> i, 0.1, 1000);
        assertEquals(570, frame.pixelScroll());
        assertEquals(19, picker.entryAt(30, 40).orElseThrow());
    }

    @Test void emptyPickerClearsOldHitsAndStyleRemainsSemanticData() {
        EditorPickerList<Integer> picker = new EditorPickerList<>();
        picker.advance(BOUNDS, BOUNDS, 2, i -> i, 0, 12);
        picker.advance(BOUNDS, BOUNDS, 0, i -> { throw new AssertionError("No entry lookup for empty lists"); }, 0, 12);
        assertTrue(picker.entryAt(30, 40).isEmpty());
        var warning = new EditorPickerList.Entry(Component.literal("cycle"), Component.literal("cannot add"),
                EditorPickerList.Tone.WARNING, false, List.of(Component.literal("warning")));
        assertEquals(EditorPickerList.Tone.WARNING, warning.tone());
        assertFalse(warning.selected());
        assertEquals("warning", warning.tooltip().getFirst().getString());
        var related = new EditorPickerList.Entry(Component.literal("draft"), Component.literal("same ID"),
                EditorPickerList.Tone.NORMAL, EditorPickerList.Highlight.SAME_BOOK, List.of());
        assertFalse(related.selected(), "a same-ID draft must not claim to be the open edit revision");
        assertEquals(EditorPickerList.Highlight.SAME_BOOK, related.highlight());
    }
}
