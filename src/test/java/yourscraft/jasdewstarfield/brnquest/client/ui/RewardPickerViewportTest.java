package yourscraft.jasdewstarfield.brnquest.client.ui;

import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPickerList;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
import static org.junit.jupiter.api.Assertions.*;

/** The type picker uses its spare validation area and every remaining pixel; scrolling reveals the final choice fully. */
class RewardPickerViewportTest {
    @Test void sevenTypesFitAtTheScreenshotGuiHeightWithoutCuttingTheLastRow() {
        var panel=new UiRect(12,12,412,236); var bounds=RewardEditorScreen.pickerBody(panel);
        var picker=new EditorPickerList<Integer>();
        var frame=picker.advanceIconChoices(bounds,panel,7,i->i,0,12);
        assertEquals(7,frame.rows().size());
        assertTrue(frame.rows().stream().allMatch(row->row.visible().equals(row.bounds())));
        assertTrue(frame.viewport().bottom()<panel.bottom()-30);
    }
    @Test void arbitrarySmallHeightsRetainTheLastChoiceAndExcludeFooterHits() {
        for(int height:new int[]{140,180,200,224,300}) {
            var panel=new UiRect(0,0,400,height); var bounds=RewardEditorScreen.pickerBody(panel);
            var picker=new EditorPickerList<Integer>();
            var frame=picker.advanceIconChoices(bounds,panel,11,i->i,0,12);
            picker.mouseScrolled(frame.viewport().centerX(),frame.viewport().centerY(),-100,30);
            frame=picker.advanceIconChoices(bounds,panel,11,i->i,.1,1000);
            var last=frame.rows().stream().filter(row->row.key()==10).findFirst().orElseThrow();
            assertEquals(last.bounds(),last.visible());
            assertEquals(10,picker.entryAt(last.visible().centerX(),last.visible().centerY()).orElseThrow());
            assertTrue(picker.entryAt(panel.centerX(),panel.bottom()-20).isEmpty());
        }
    }
}
