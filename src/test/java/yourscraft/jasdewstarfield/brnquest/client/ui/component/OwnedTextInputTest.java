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
    @Test void verticalMovementStartsFromTheCurrentWidthAfterShortRowsAndExplicitRepositioning() {
        var field=new EditorSourceField(font(),new UiRect(0,0,200,80),Component.literal("Body"));
        field.setValue("abcdefghij\nxy\nabcdefghij"); field.setSelection(8,8);
        field.moveVertical(1,false); assertEquals(13,field.cursor()); // Short row clamps to its end.
        field.moveVertical(1,false); assertEquals(16,field.cursor()); // Use that end's width, not the old column eight.
        field.moveVertical(-1,false); field.moveVertical(1,false); assertEquals(16,field.cursor());
        field.setSelection(20,20); field.moveVertical(-1,true);
        assertEquals(13,field.cursor()); assertEquals(20,field.anchor());
        field.moveVertical(-1,true); assertEquals(2,field.cursor()); assertEquals(20,field.anchor());
    }
    @Test void wrappedRowEndsKeepTheirVisualRowAndDoNotTrapUpwardMovement() {
        var field=new EditorSourceField(font(),new UiRect(0,0,13,80),Component.literal("Body"));
        field.setValue("abcdefghijk"); field.setSelection(8,8); // Four glyphs per visual row.
        field.moveVertical(-1,false); assertEquals(4,field.cursor());
        field.setSelection(7,7); field.moveVertical(-1,false); assertEquals(3,field.cursor());
        field.moveVertical(1,false); assertEquals(7,field.cursor());
        field.setSelection(11,11); field.moveVertical(-1,false); assertEquals(7,field.cursor());
        // Going to an earlier wrap-end must display the caret at that row's end, not on the next row.
        field.buffer().load("abcd\nabcdefghijk"); field.setSelection(4,4);
        field.moveVertical(1,false); assertEquals(9,field.cursor()); assertEquals(9,field.imeGeometry().caretX());
        assertEquals(15,field.imeGeometry().caretY());
        field.moveVertical(-1,false); assertEquals(4,field.cursor()); assertEquals(4,field.imeGeometry().caretY());
        field.moveVertical(1,false); field.moveVertical(1,false); assertEquals(26,field.imeGeometry().caretY());
        field.setWidth(200); assertEquals(15,field.imeGeometry().caretY()); // New wrapping drops the old row hint.
    }
    @Test void verticalPickingUsesGlyphWidthsInsteadOfCharacterCounts() {
        Font mixed=new Font(id->null,false) {
            @Override public StringSplitter getSplitter() { return new StringSplitter((cp,style)->cp=='中'?4:1); }
        };
        var field=new EditorSourceField(mixed,new UiRect(0,0,100,80),Component.literal("Body"));
        field.setValue("中ab\nabcdefg"); field.setSelection(1,1);
        field.moveVertical(1,false); assertEquals(8,field.cursor()); // Four pixels maps to four narrow characters.
        field.moveVertical(-1,false); assertEquals(1,field.cursor());
    }
}
