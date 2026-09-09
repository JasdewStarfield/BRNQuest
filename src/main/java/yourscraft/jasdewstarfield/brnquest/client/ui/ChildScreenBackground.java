package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/** Keeps suspended parents at the active child's logical size, including nested picker chains. */
final class ChildScreenBackground {
    private ChildScreenBackground() {}
    static void render(Screen parent, GuiGraphics graphics, int width, int height, float partialTick) {
        resizeIfNeeded(parent, width, height);
        parent.render(graphics, -1, -1, partialTick);
    }
    static void resizeIfNeeded(Screen parent, int width, int height) {
        // Reuse the normal resize lifecycle so existing fields retain their drafts and rebuild hit geometry.
        // Reinitializing every frame would instead reset selection, scrolling and outstanding queries.
        if (parent.width != width || parent.height != height) parent.resize(Minecraft.getInstance(), width, height);
    }
}
