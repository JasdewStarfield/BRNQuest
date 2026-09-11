package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Shared editor paint with vanilla widget focus, narration and keyboard activation. */
public final class EditorButtonWidget extends Button {
    public EditorButtonWidget(int x, int y, int width, int height, Component label, OnPress action) {
        super(x, y, width, height, label, action, DEFAULT_NARRATION);
    }

    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Only replace paint: the Screen still owns tab order and dispatches each activation once.
        EditorButton.render(graphics, Minecraft.getInstance().font,
                new UiRect(getX(), getY(), getX() + getWidth(), getY() + getHeight()),
                EditorButton.Definition.text(getMessage(), null),
                new EditorButton.State(active, isHovered(), isFocused()), EditorButton.Tone.NEUTRAL.palette());
    }
}
