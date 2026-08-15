package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Stateless editor button renderer; actions remain owned by the screen. */
public final class EditorButton {
    private EditorButton() {}

    public static void render(GuiGraphics graphics, Font font, UiRect bounds, Component label,
                              int backgroundColor, int textColor, int textOffsetY) {
        graphics.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), backgroundColor);
        graphics.drawCenteredString(font, label, bounds.centerX(), bounds.top() + textOffsetY, textColor);
    }
}
