package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

/** Stateful edits cover selection replacement, Unicode boundaries, history and silent draft rebinding. */
class TextEditBufferTest {
    @Test void replacementAtTheLimitAndFormatTokensAreLossless() {
        var buffer=new TextEditBuffer(false,8); buffer.nativeCodes(true); buffer.load("§6abcdef");
        buffer.selection(8,2);
        assertTrue(buffer.insert("§lXYZ",TextEditBuffer.Kind.FORMAT,0));
        assertEquals("§6§lXYZ",buffer.value());
        assertEquals(7,buffer.cursor()); assertTrue(buffer.undo()); assertEquals("§6abcdef",buffer.value());
    }
    @Test void typingGroupsButCursorMovementAndFormattingCreateSeparateUndoSteps() {
        var buffer=new TextEditBuffer(false,256);
        buffer.insert("a",TextEditBuffer.Kind.TYPE,0); buffer.insert("b",TextEditBuffer.Kind.TYPE,100);
        buffer.insert("c",TextEditBuffer.Kind.TYPE,200); assertTrue(buffer.undo()); assertEquals("",buffer.value());
        assertTrue(buffer.redo()); assertEquals("abc",buffer.value());
        buffer.move(1,false); buffer.insert("X",TextEditBuffer.Kind.TYPE,300);
        assertTrue(buffer.undo()); assertEquals("abc",buffer.value());
        assertTrue(buffer.redo()); assertEquals("aXbc",buffer.value());
    }
    @Test void surrogatePairsAreAtomicForMovementDeletionAndLengthLimits() {
        var buffer=new TextEditBuffer(false,4); buffer.load("A😀B");
        buffer.move(3,false); buffer.horizontal(-1,false,false); assertEquals(1,buffer.cursor());
        buffer.delete(1,false,0); assertEquals("AB",buffer.value());
        buffer.undo(); assertEquals("A😀B",buffer.value());
        var limited=new TextEditBuffer(false,1); assertFalse(limited.insert("😀",TextEditBuffer.Kind.PASTE,0));
        assertEquals("",limited.value());
    }
    @Test void snapshotRestoreRetainsSelectionAndHistoryAcrossResizeAndLocaleReturn() {
        var buffer=new TextEditBuffer(true,32_768); buffer.nativeCodes(true); buffer.load("one\ntwo");
        buffer.move(3,false); buffer.insert("§l",TextEditBuffer.Kind.FORMAT,0); buffer.selection(5,3);
        var saved=buffer.snapshot(); var restored=new TextEditBuffer(true,32_768); restored.nativeCodes(true); restored.restore(saved);
        assertEquals("§l",restored.selectedText()); assertTrue(restored.undo()); assertEquals("one\ntwo",restored.value());
        assertTrue(restored.redo()); assertEquals(saved.selection().text(),restored.value());
    }
    @Test void normalizationFilteringValidationAndNoChangeEventsRespectFieldPolicy() {
        var buffer=new TextEditBuffer(true,100); buffer.nativeCodes(true);
        var changes=new ArrayList<String>(); buffer.listener(changes::add);
        buffer.insert("§6A\r\n😀\u0000",TextEditBuffer.Kind.PASTE,0); assertEquals("§6A\n😀",buffer.value());
        int count=changes.size(); buffer.load(buffer.value()); assertEquals(count,changes.size());
        var numeric=new TextEditBuffer(false,10); numeric.validator(text->text.matches("-?[0-9]*"));
        assertTrue(numeric.insert("12",TextEditBuffer.Kind.PASTE,0)); assertFalse(numeric.insert("x",TextEditBuffer.Kind.TYPE,1));
        assertEquals("12",numeric.value());
    }
    @Test void historyIsBoundedAndNewTypingDiscardsRedo() {
        var buffer=new TextEditBuffer(true,32_768); buffer.load("x".repeat(32_000));
        for(int i=0;i<90;i++) buffer.insert("x",TextEditBuffer.Kind.PASTE,i);
        assertTrue(buffer.snapshot().undo().size()<=64);
        assertTrue(buffer.snapshot().undo().stream().mapToLong(s->s.text().length()).sum()<=262_144);
        assertTrue(buffer.undo()); buffer.insert("Y",TextEditBuffer.Kind.TYPE,1000); assertFalse(buffer.redo());
    }
    @Test void inputMethodProbeDoesNotEraseSelectionAndSupplementaryCharEventsJoin() {
        var buffer=new TextEditBuffer(false,256); buffer.load("selected"); buffer.selection(8,0);
        buffer.type('\u0000',0); assertEquals("selected",buffer.value()); assertEquals("selected",buffer.selectedText());
        buffer.type('\uD83D',1); buffer.type('\uDE00',2); assertEquals("😀",buffer.value());
        assertTrue(buffer.undo()); assertEquals("selected",buffer.value()); assertEquals("selected",buffer.selectedText());
    }
    @Test void deletionBurstRestoresTheOriginalCaret() {
        var buffer=new TextEditBuffer(false,256); buffer.load("abcd");
        buffer.delete(-1,false,0); buffer.delete(-1,false,100);
        assertTrue(buffer.undo()); assertEquals("abcd",buffer.value()); assertEquals(4,buffer.cursor());
    }
}
