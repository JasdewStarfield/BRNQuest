package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Reusable searchable two-line picker for dependencies and future type selectors. */
public final class EditorPickerList {
    public static final int SEARCH_HEIGHT = 18;
    public static final int ROW_HEIGHT = 30;

    public enum Tone { NORMAL, WARNING, DISABLED }

    public record Entry(Component primary, Component secondary, Tone tone) {}

    private EditorPickerList() {}

    public static int visibleRows(UiRect bounds) {
        return Math.max(1, (bounds.height() - SEARCH_HEIGHT - 4) / ROW_HEIGHT);
    }

    /** Returns an absolute entry index or -1 when the pointer is outside a visible row. */
    public static int entryAt(UiRect bounds, int firstIndex, int entryCount, double mouseX, double mouseY) {
        if (!bounds.contains(mouseX, mouseY)) return -1;
        int rowsTop = bounds.top() + SEARCH_HEIGHT + 2;
        if (mouseY < rowsTop) return -1;
        int row = ((int) mouseY - rowsTop) / ROW_HEIGHT;
        if (row < 0 || row >= visibleRows(bounds)) return -1;
        int index = firstIndex + row;
        return index >= 0 && index < entryCount ? index : -1;
    }

    public static void render(GuiGraphics graphics, Font font, UiRect bounds, Component searchText,
                              boolean showingHint, List<Entry> entries, int firstIndex,
                              int mouseX, int mouseY) {
        graphics.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), 0xFA202832);
        graphics.fill(bounds.left() + 2, bounds.top() + 2, bounds.right() - 2,
                bounds.top() + SEARCH_HEIGHT, 0xFF151A22);
        graphics.drawString(font, searchText, bounds.left() + 6, bounds.top() + 6,
                showingHint ? 0xFF7F8B99 : 0xFFFFFFFF, false);

        int visibleRows = visibleRows(bounds);
        for (int row = 0; row < visibleRows && firstIndex + row < entries.size(); row++) {
            int index = firstIndex + row;
            Entry entry = entries.get(index);
            int top = bounds.top() + SEARCH_HEIGHT + 2 + row * ROW_HEIGHT;
            boolean hovered = mouseX >= bounds.left() + 2 && mouseX <= bounds.right() - 2
                    && mouseY >= top && mouseY < top + ROW_HEIGHT;
            int background = hovered ? 0xE0343D49 : 0xA02A323E;
            int primaryColor = switch (entry.tone()) {
                case NORMAL -> 0xFFFFFFFF;
                case WARNING -> 0xFFFFA070;
                case DISABLED -> 0xFF7F8B99;
            };
            graphics.fill(bounds.left() + 2, top, bounds.right() - 2, top + ROW_HEIGHT, background);
            graphics.drawString(font, Component.literal(font.plainSubstrByWidth(
                            entry.primary().getString(), bounds.width() - 16)),
                    bounds.left() + 6, top + 4, primaryColor, false);
            graphics.drawString(font, Component.literal(font.plainSubstrByWidth(
                            entry.secondary().getString(), bounds.width() - 16)),
                    bounds.left() + 6, top + 16, 0xFF9FB0C2, false);
        }

        EditorScrollbar.render(graphics, bounds.right() - 4, bounds.top() + SEARCH_HEIGHT + 2, bounds.bottom() - 2,
                entries.size() * ROW_HEIGHT, visibleRows * ROW_HEIGHT, firstIndex * (double) ROW_HEIGHT);
    }
}
