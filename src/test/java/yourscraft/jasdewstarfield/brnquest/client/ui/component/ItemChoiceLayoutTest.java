package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** GUI-scale-sized panels must fit the entire inventory above the footer, using the same layout as painting and hits. */
class ItemChoiceLayoutTest {
    @Test void allThirtySixSlotsStayAboveButtonsAcrossSupportedGuiSizes() {
        for(int height:new int[]{232,240,248,262,274}) {
            var panel=new UiRect(20,10,240,10+height); var layout=new ItemChoiceLayout(panel,true);
            assertTrue(layout.candidates().height()>0);
            for(int index=0;index<36;index++) {
                var slot=layout.inventorySlot(index);
                assertEquals(slot,slot.intersection(layout.inventoryClip()));
                assertTrue(slot.bottom()<=panel.bottom()-27);
            }
            assertEquals(72,layout.inventorySlot(8).bottom()-layout.inventorySlot(9).top());
        }
    }
    @Test void tinyWindowsDoNotAllocateCandidatePixelsIntoFixedControls() {
        var panel=new UiRect(0,0,220,200); var layout=new ItemChoiceLayout(panel,true);
        assertEquals(0,layout.candidates().height());
        assertTrue(layout.inventoryClip().bottom()<=panel.bottom()-22);
        assertNotEquals(layout.inventorySlot(0),layout.inventorySlot(0).intersection(layout.inventoryClip()));
        assertThrows(IllegalArgumentException.class,()->layout.inventorySlot(36));
    }
}
