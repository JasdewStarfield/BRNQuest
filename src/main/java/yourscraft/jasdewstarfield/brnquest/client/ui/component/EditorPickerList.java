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
    public static int entryAt(UiRect bounds, EditorSmoothScroll scroll, int entryCount,
                              double mouseX, double mouseY) {
        if (!bounds.contains(mouseX, mouseY)) return -1;
        if (mouseX >= bounds.right() - 6) return -1;
        int rowsTop = bounds.top() + SEARCH_HEIGHT + 2;
        int rowsBottom = rowsTop + visibleRows(bounds) * ROW_HEIGHT;
        return scroll.rowAt(mouseY, rowsTop, rowsBottom, ROW_HEIGHT, entryCount);
    }

    public static void render(GuiGraphics graphics, Font font, UiRect bounds, Component searchText,
                              boolean showingHint, List<Entry> entries, EditorSmoothScroll scroll,
                              double elapsedSeconds, double smoothSpeed, int mouseX, int mouseY) {
        graphics.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), 0xFA202832);
        graphics.fill(bounds.left() + 2, bounds.top() + 2, bounds.right() - 2,
                bounds.top() + SEARCH_HEIGHT, 0xFF151A22);
        graphics.drawString(font, searchText, bounds.left() + 6, bounds.top() + 6,
                showingHint ? 0xFF7F8B99 : 0xFFFFFFFF, false);

        int visibleRows = visibleRows(bounds);
        int rowsTop = bounds.top() + SEARCH_HEIGHT + 2;
        int rowsBottom = rowsTop + visibleRows * ROW_HEIGHT;
        scroll.frameAndRender(graphics, bounds.right() - 4, rowsTop, rowsBottom,
                entries.size() * ROW_HEIGHT, visibleRows * ROW_HEIGHT, elapsedSeconds, smoothSpeed);
        int firstIndex = scroll.firstIndex(ROW_HEIGHT);
        int rowOffset = scroll.rowOffset(ROW_HEIGHT);
        graphics.enableScissor(bounds.left(), rowsTop, bounds.right(), rowsBottom);
        for (int row = 0; row <= visibleRows && firstIndex + row < entries.size(); row++) {
            int index = firstIndex + row;
            Entry entry = entries.get(index);
            int top = rowsTop + rowOffset + row * ROW_HEIGHT;
            boolean hovered = mouseX >= bounds.left() + 2 && mouseX <= bounds.right() - 6
                    && mouseY >= top && mouseY < top + ROW_HEIGHT;
            int background = hovered ? 0xE0343D49 : 0xA02A323E;
            int primaryColor = switch (entry.tone()) {
                case NORMAL -> 0xFFFFFFFF;
                case WARNING -> 0xFFFFA070;
                case DISABLED -> 0xFF7F8B99;
            };
            // Reserve the rightmost strip for the scrollbar rendered by the shared scroll state.
            graphics.fill(bounds.left() + 2, top, bounds.right() - 6, top + ROW_HEIGHT, background);
            graphics.drawString(font, Component.literal(font.plainSubstrByWidth(
                            entry.primary().getString(), bounds.width() - 20)),
                    bounds.left() + 6, top + 4, primaryColor, false);
            graphics.drawString(font, Component.literal(font.plainSubstrByWidth(
                            entry.secondary().getString(), bounds.width() - 20)),
                    bounds.left() + 6, top + 16, 0xFF9FB0C2, false);
        }
        graphics.disableScissor();
    }
}
