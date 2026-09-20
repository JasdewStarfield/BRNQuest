package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/** Keeps suspended parents at the active child's logical size, including nested picker chains. */
public final class ChildScreenBackground {
    private ChildScreenBackground() {}
    public static void render(Screen parent, GuiGraphics graphics, int width, int height, float partialTick) {
        resizeIfNeeded(parent, width, height);
        parent.render(graphics, -1, -1, partialTick);
        // Keep the parent's color image but start a fresh depth layer for every nested child screen.
        // Item renderers and modal parents use different Z values; those must never cover child controls.
        graphics.flush();
        com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }
    public static void resizeIfNeeded(Screen parent, int width, int height) {
        // Reuse the normal resize lifecycle so existing fields retain their drafts and rebuild hit geometry.
        // Reinitializing every frame would instead reset selection, scrolling and outstanding queries.
        if (parent.width != width || parent.height != height) parent.resize(Minecraft.getInstance(), width, height);
    }
}
