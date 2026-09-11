package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EditorItemSelectorLayoutTest {
    @Test void sharedSlotEdgesSelectOnlyTheNextSlot() {
        EditorItemSelectorLayout layout = new EditorItemSelectorLayout(320, 240);
        // Include the outer border, but never let adjacent slots both claim the same pixel.
        assertEquals(9, layout.inventoryIndexAt(79, 100));
        assertEquals(9, layout.inventoryIndexAt(96.99, 100));
        assertEquals(10, layout.inventoryIndexAt(97, 100));
        assertEquals(18, layout.inventoryIndexAt(79, 118));
        assertEquals(-1, layout.inventoryIndexAt(241, 100));
        assertEquals(-1, layout.inventoryIndexAt(79, 176));
    }

    @Test void mapsVanillaMainInventoryAndHotbarOrder() {
        EditorItemSelectorLayout layout = new EditorItemSelectorLayout(320, 240);

        assertEquals(new UiRect(79, 100, 97, 118), layout.inventorySlot(9));
        assertEquals(new UiRect(223, 136, 241, 154), layout.inventorySlot(35));
        assertEquals(new UiRect(79, 158, 97, 176), layout.inventorySlot(0));
        assertEquals(new UiRect(223, 158, 241, 176), layout.inventorySlot(8));
    }

    @Test void hitTestingReturnsTheRenderedInventoryIndex() {
        EditorItemSelectorLayout layout = new EditorItemSelectorLayout(320, 240);

        assertEquals(9, layout.inventoryIndexAt(80, 101));
        assertEquals(35, layout.inventoryIndexAt(224, 137));
        assertEquals(0, layout.inventoryIndexAt(80, 159));
        assertEquals(-1, layout.inventoryIndexAt(10, 10));
    }

    @Test void rejectsIndicesOutsideThePlayerStorageRange() {
        EditorItemSelectorLayout layout = new EditorItemSelectorLayout(320, 240);

        assertThrows(IllegalArgumentException.class, () -> layout.inventorySlot(-1));
        assertThrows(IllegalArgumentException.class, () -> layout.inventorySlot(36));
    }
}
