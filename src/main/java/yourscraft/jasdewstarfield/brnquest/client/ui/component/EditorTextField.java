package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** EditBox with the editor's readable colors and explicit show/hide lifecycle. */
public final class EditorTextField extends EditBox {
    public EditorTextField(Font font, Component narration, int maximumLength) {
        super(font, 0, 0, 10, 18, narration);
        setMaxLength(maximumLength);
        setTextColor(0xFFFFFFFF);
        setTextColorUneditable(0xFFB7C5D8);
        setBordered(true);
        hide();
    }

    public void show(UiRect bounds, boolean enabled) {
        boolean geometryChanged = !visible || getX() != bounds.left() || getY() != bounds.top()
                || getWidth() != bounds.width();
        setX(bounds.left());
        setY(bounds.top());
        setWidth(bounds.width());
        setVisible(true);
        active = enabled;
        if (geometryChanged) {
            // setValue() may run while a hidden field still has its 10px constructor width.
            // Re-applying the cursor after sizing makes EditBox recompute its horizontal text viewport.
            setCursorPosition(getCursorPosition());
        }
    }

    public void hide() {
        setVisible(false);
        active = false;
    }

    /** Moves a visible field with its drawer while disabling input until the drawer settles. */
    public void offsetForDrawerAnimation(int offsetX) {
        if (!visible || offsetX == 0) return;
        setX(getX() + offsetX);
        active = false;
    }
}
