package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.gui.components.events.GuiEventListener;

/** Common owned input contract for formatting, history, focus guards and optional input-method adapters. */
public interface EditorTextInput extends GuiEventListener {
    TextEditBuffer buffer();
    boolean acceptsText();
    default int cursor() { return buffer().cursor(); }
    default int anchor() { return buffer().anchor(); }
}
