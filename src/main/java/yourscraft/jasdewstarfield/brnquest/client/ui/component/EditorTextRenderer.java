package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Shared text drawing; scaling changes presentation, never input or icon geometry. */
public final class EditorTextRenderer {
    private EditorTextRenderer() {}
    public static int drawWrapped(GuiGraphics graphics, Font font, String text, int x, int y, int width, int color) {
        Component component = NativeText.component(text);
        var lines = MixedTextLayout.split(font, component, width);
        for (int index = 0; index < lines.size(); index++) {
            graphics.drawString(font, lines.get(index), x, y + index * font.lineHeight, color, false);
        }
        return y + lines.size() * font.lineHeight;
    }

    public static void drawFittedString(GuiGraphics graphics, Font font, Component text, int x, int y,
                                  int maximumWidth, int color, float minimumScale) {
        int measuredWidth = font.width(NativeText.sequence(text));
        float scale = EditorTextLayout.fittedScale(measuredWidth, maximumWidth, minimumScale);
        int unscaledWidth = Math.max(1, (int) Math.floor(maximumWidth / scale));
        var visible = clip(font, text, unscaledWidth);
        TextLayoutDebug.fitted(graphics, font, text, text.getString(),
                new UiRect(x, y, x + Math.max(0, maximumWidth), y + font.lineHeight), x, y, scale, "fitted");
        TextLayoutDebug.mute();
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, visible, 0, 0, color, false);
        graphics.pose().popPose();
        TextLayoutDebug.unmute();
    }

    public static void drawFittedStringRight(GuiGraphics graphics, Font font, Component text, int right, int y,
                                       int maximumWidth, int color, float minimumScale) {
        int measuredWidth = font.width(NativeText.sequence(text));
        float scale = EditorTextLayout.fittedScale(measuredWidth, maximumWidth, minimumScale);
        int unscaledWidth = Math.max(1, (int) Math.floor(maximumWidth / scale));
        var visible = clip(font, text, unscaledWidth);
        int left = right - Math.round(font.width(visible) * scale);
        TextLayoutDebug.fitted(graphics, font, text, text.getString(),
                new UiRect(right - Math.max(0, maximumWidth), y, right, y + font.lineHeight),
                left, y, scale, "fitted_right");
        TextLayoutDebug.mute();
        graphics.pose().pushPose();
        graphics.pose().translate(left, y, 0);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, visible, 0, 0, color, false);
        graphics.pose().popPose();
        TextLayoutDebug.unmute();
    }

    /** Clips the styled sequence rather than flattening components into an unstyled String. */
    private static net.minecraft.util.FormattedCharSequence clip(Font font, Component text, int maximum) {
        var sequence = NativeText.sequence(text);
        float[] width = {0};
        java.util.List<net.minecraft.util.FormattedCharSequence> glyphs = new java.util.ArrayList<>();
        sequence.accept((index, style, codepoint) -> {
            var glyph = net.minecraft.util.FormattedCharSequence.codepoint(codepoint, style);
            float next = width[0] + font.getSplitter().stringWidth(glyph);
            if (next > maximum) return false;
            width[0] = next; glyphs.add(glyph); return true;
        });
        return net.minecraft.util.FormattedCharSequence.composite(glyphs);
    }
}
