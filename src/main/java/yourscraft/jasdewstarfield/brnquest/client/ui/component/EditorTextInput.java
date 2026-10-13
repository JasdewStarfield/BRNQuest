package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.gui.components.events.GuiEventListener;

/** Common owned input contract for formatting, history, focus guards and optional input-method adapters. */
public interface EditorTextInput extends GuiEventListener {
    /** GUI coordinates; the optional IME adapter applies its own window/DPI scale. */
    record ImeGeometry(int x,int y,int width,int height,int caretX,int caretY,int fontHeight) {}
    ImeGeometry imeGeometry();
    TextEditBuffer buffer();
    boolean acceptsText();
    default int cursor() { return buffer().cursor(); }
    default int anchor() { return buffer().anchor(); }
}
