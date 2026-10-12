package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.FormattedCharSequence;
import yourscraft.jasdewstarfield.brnquest.client.mixin.MultiLineEditBoxAccessor;
import yourscraft.jasdewstarfield.brnquest.client.mixin.MultilineTextFieldAccessor;

/** Literal source renderer uses the same line ranges as the opted-in vanilla text buffer. */
public final class EditorSourceField extends MultiLineEditBox {
    private final Font sourceFont;
    private String cachedSource;
    private int cachedWidth;
    private java.util.List<RawTextLayout.Line> cachedLines;
    public EditorSourceField(Font font, UiRect bounds, Component narration) {
        super(font, bounds.left(), bounds.top(), bounds.width(), bounds.height(), Component.empty(), narration);
        sourceFont = font;
        ((RawTextInput) field()).brnquest$rawText(true);
        setValue("");
    }
    private MultilineTextField field() { return ((MultiLineEditBoxAccessor) (Object) this).brnquest$textField(); }
    @Override public boolean charTyped(char character, int modifiers) {
        if (character == '§' && visible && active && isFocused()) { field().insertText("§"); return true; }
        return super.charTyped(character, modifiers);
    }
    public double savedScroll() { return scrollAmount(); }
    public void restoreScroll(double value) { setScrollAmount(value); }
    @Override protected void renderDecorations(GuiGraphics graphics) {
        // The owning screen keeps character count in its fixed footer.
    }
    @Override protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        String text = getValue();
        var selection = (MultilineTextFieldAccessor) field();
        int cursor = selection.brnquest$cursor(), anchor = selection.brnquest$selectCursor();
        int start = Math.min(cursor, anchor), end = Math.max(cursor, anchor);
        int x = getX() + innerPadding(), y = getY() + innerPadding();
        int width = getWidth() - totalInnerPadding();
        if (!text.equals(cachedSource) || width != cachedWidth) {
            cachedSource = text; cachedWidth = width;
            cachedLines = RawTextLayout.lines(text, width, value -> RawTextLayout.width(sourceFont, value));
        }
        var lines = cachedLines;
        for (int index = 0; index < lines.size(); index++) {
            var line = lines.get(index);
            int rowY = y + index * sourceFont.lineHeight;
            if (!withinContentAreaTopBottom(rowY, rowY + sourceFont.lineHeight)) continue;
            graphics.drawString(sourceFont, FormattedCharSequence.forward(text.substring(line.start(), line.end()), Style.EMPTY), x, rowY, 0xFFE0E0E0, false);
            if (isFocused() && Util.getMillis() / 500 % 2 == 0 && index == field().getLineAtCursor()) {
                int cursorX = x + RawTextLayout.width(sourceFont, text.substring(line.start(), cursor));
                graphics.fill(cursorX, rowY - 1, cursorX + 1, rowY + sourceFont.lineHeight, 0xFFE0E0E0);
            }
            if (end > line.start() && start < line.end()) {
                int left = x + RawTextLayout.width(sourceFont, text.substring(line.start(), Math.max(line.start(), start)));
                int right = x + RawTextLayout.width(sourceFont, text.substring(line.start(), Math.min(line.end(), end)));
                graphics.fill(RenderType.guiTextHighlight(), left, rowY, right, rowY + sourceFont.lineHeight, 0xFF0000FF);
            }
        }
    }
}
