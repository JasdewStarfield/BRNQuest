package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Widget-level checks cover selection editing, literal measurement, hidden state and resize restoration. */
class OwnedTextInputTest {
    private Font font() {
        return new Font(id->null,false) {
            @Override public StringSplitter getSplitter() { return new StringSplitter((cp,style)->1.25F); }
        };
    }
    @Test void singleLineEditsAndScrollingUseLiteralFormattingCodes() {
        var field=new EditorTextField(font(),Component.literal("Title"),256);
        field.show(new UiRect(10,20,30,38),true); field.setNativeFormatting(true); field.setValue("§6"+"text".repeat(20));
        field.setSelection(4,2); field.insertText("😀");
        assertEquals("§6😀xt"+"text".repeat(19),field.getValue());
        assertTrue(field.buffer().undo()); assertEquals("te",field.getHighlighted());
        field.setCursorPosition(field.getValue().length());
        var box=field.imeGeometry(); assertTrue(box.caretX()<=box.width());
        field.setFocused(true); assertTrue(field.acceptsText()); field.hide(); assertFalse(field.acceptsText());
    }
    @Test void multilineSnapshotsKeepSourceSelectionAndHistoryWhenTheWidthChanges() {
        var field=new EditorSourceField(font(),new UiRect(0,0,40,35),Component.literal("Body"));
        field.setValue("one\ntwo\n"+"x".repeat(300)); field.setSelection(7,4);
        field.applyEdit("one\n§ltwo§r\n"+"x".repeat(300),9,6);
        var state=field.buffer().snapshot();
        var resized=new EditorSourceField(font(),new UiRect(0,0,20,25),Component.literal("Body"));
        resized.restoreState(state); assertEquals(field.getValue(),resized.getValue());
        assertEquals(9,resized.cursor()); assertEquals(6,resized.anchor());
        assertTrue(resized.buffer().undo()); assertEquals("two",resized.buffer().selectedText());
        resized.setSelection(resized.getValue().length(),resized.getValue().length());
        assertTrue(resized.savedScroll()>0); assertTrue(resized.imeGeometry().caretY()<resized.getHeight());
    }
}
