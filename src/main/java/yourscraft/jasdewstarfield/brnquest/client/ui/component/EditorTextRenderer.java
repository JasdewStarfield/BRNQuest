package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Shared text drawing; scaling changes presentation, never input or icon geometry. */
public final class EditorTextRenderer {
    private EditorTextRenderer() {}
    public static int drawWrapped(GuiGraphics graphics, Font font, String text, int x, int y, int width, int color) {
        Component component = Component.literal(text);
        var lines = MixedTextLayout.split(font, component, width);
        for (int index = 0; index < lines.size(); index++) {
            graphics.drawString(font, lines.get(index), x, y + index * font.lineHeight, color, false);
        }
        return y + lines.size() * font.lineHeight;
    }

    public static void drawFittedString(GuiGraphics graphics, Font font, Component text, int x, int y,
                                  int maximumWidth, int color, float minimumScale) {
        int measuredWidth = font.width(text);
        float scale = EditorTextLayout.fittedScale(measuredWidth, maximumWidth, minimumScale);
        int unscaledWidth = Math.max(1, (int) Math.floor(maximumWidth / scale));
        String visible = font.plainSubstrByWidth(text.getString(), unscaledWidth);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, visible, 0, 0, color, false);
        graphics.pose().popPose();
    }

    public static void drawFittedStringRight(GuiGraphics graphics, Font font, Component text, int right, int y,
                                       int maximumWidth, int color, float minimumScale) {
        int measuredWidth = font.width(text);
        float scale = EditorTextLayout.fittedScale(measuredWidth, maximumWidth, minimumScale);
        int unscaledWidth = Math.max(1, (int) Math.floor(maximumWidth / scale));
        String visible = font.plainSubstrByWidth(text.getString(), unscaledWidth);
        int left = right - Math.round(font.width(visible) * scale);
        graphics.pose().pushPose();
        graphics.pose().translate(left, y, 0);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, visible, 0, 0, color, false);
        graphics.pose().popPose();
    }
}
