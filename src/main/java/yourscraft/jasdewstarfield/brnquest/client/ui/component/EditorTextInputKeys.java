package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Shared keyboard commands keep text history local to the focused field, with visual row boundaries from its view. */
final class EditorTextInputKeys {
    private EditorTextInputKeys() {}
    static boolean key(TextEditBuffer buffer,int key,int rowStart,int rowEnd,boolean multiline) {
        boolean control=Screen.hasControlDown(), shift=Screen.hasShiftDown();
        long now=Util.getMillis(); buffer.clearPendingCharacter();
        if(control && key==90) { if(shift) buffer.redo(); else buffer.undo(); return true; }
        if(control && key==89) { buffer.redo(); return true; }
        if(Screen.isSelectAll(key)) { buffer.selection(buffer.value().length(),0); return true; }
        if(Screen.isCopy(key)) { Minecraft.getInstance().keyboardHandler.setClipboard(buffer.selectedText()); return true; }
        if(Screen.isCut(key)) {
            Minecraft.getInstance().keyboardHandler.setClipboard(buffer.selectedText());
            if(buffer.selected()) buffer.insert("",TextEditBuffer.Kind.PASTE,now); return true;
        }
        if(Screen.isPaste(key)) {
            buffer.insert(Minecraft.getInstance().keyboardHandler.getClipboard(),TextEditBuffer.Kind.PASTE,now); return true;
        }
        return switch(key) {
            case 259 -> { buffer.delete(-1,control,now); yield true; }
            case 261 -> { buffer.delete(1,control,now); yield true; }
            case 262 -> { buffer.horizontal(1,shift,control); yield true; }
            case 263 -> { buffer.horizontal(-1,shift,control); yield true; }
            case 268 -> { buffer.move(control?0:rowStart,shift); yield true; }
            case 269 -> { buffer.move(control?buffer.value().length():rowEnd,shift); yield true; }
            case 257,335 -> {
                if(multiline) buffer.insert("\n",TextEditBuffer.Kind.TYPE,now);
                yield multiline;
            }
            default -> false;
        };
    }
}
