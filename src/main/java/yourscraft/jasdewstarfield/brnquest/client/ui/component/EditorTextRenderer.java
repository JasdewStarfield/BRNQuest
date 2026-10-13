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

    /** Compact captions show an omission marker and defer full hover text until all screen content is painted. */
    public static void drawCaption(GuiGraphics graphics, Font font, Component text, UiRect bounds,
                                   int color, int mouseX, int mouseY) {
        drawCaption(graphics, font, text, bounds, bounds, color, mouseX, mouseY);
    }

    /** Centered headings use the same omission and hover policy as compact left-aligned captions. */
    public static void drawCaptionCentered(GuiGraphics graphics, Font font, Component text, UiRect bounds,
                                           int color, int mouseX, int mouseY) {
        int width = Math.min(bounds.width(), font.width(NativeText.sequence(text)));
        int left = bounds.centerX() - width / 2;
        drawCaption(graphics, font, text, new UiRect(left, bounds.top(), left+width, bounds.bottom()),
                color, mouseX, mouseY);
    }

    /** A scrolling caption keeps its authored baseline while only its visible pixels can request hover help. */
    public static void drawCaption(GuiGraphics graphics, Font font, Component text, UiRect bounds, UiRect clip,
                                   int color, int mouseX, int mouseY) {
        if (bounds.width() == 0 || bounds.height() == 0) return;
        var sequence = NativeText.sequence(text);
        boolean omitted = font.width(sequence) > bounds.width();
        var visible = EditorTextLayout.ellipsizeSequence(sequence, bounds.width(),
                font.getSplitter()::stringWidth, text.getStyle());
        graphics.drawString(font, visible, bounds.left(), bounds.top(), color, false);
        if (omitted && bounds.intersection(clip).containsExclusive(mouseX, mouseY)) {
            var screen = net.minecraft.client.Minecraft.getInstance().screen;
            if (screen != null) screen.setTooltipForNextRenderPass(text);
        }
    }

    /** Scaling has a lower bound; remaining overflow keeps styling and a visible omission marker. */
    private static net.minecraft.util.FormattedCharSequence clip(Font font, Component text, int maximum) {
        return EditorTextLayout.ellipsizeSequence(NativeText.sequence(text), maximum,
                font.getSplitter()::stringWidth, text.getStyle());
    }
}
