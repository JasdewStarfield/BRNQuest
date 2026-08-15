package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Shared clamped popup menu with deterministic row hit testing. */
public final class EditorPopupMenu {
    public static final int ROW_HEIGHT = 18;

    public record Entry(Component label, boolean dangerous) {}

    private EditorPopupMenu() {}

    public static UiRect layout(int anchorX, int anchorY, int screenWidth, int contentTop, int contentBottom,
                                int menuWidth, int rowCount) {
        int menuHeight = Math.max(ROW_HEIGHT, rowCount * ROW_HEIGHT);
        int left = Math.max(4, Math.min(anchorX, screenWidth - menuWidth - 4));
        int top = Math.max(contentTop, Math.min(anchorY, contentBottom - menuHeight));
        return new UiRect(left, top, left + menuWidth, top + menuHeight);
    }

    public static void render(GuiGraphics graphics, Font font, UiRect bounds, List<Entry> entries) {
        graphics.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), 0xFA202832);
        for (int index = 0; index < entries.size(); index++) {
            int top = bounds.top() + index * ROW_HEIGHT;
            Entry entry = entries.get(index);
            graphics.fill(bounds.left() + 1, top + 1, bounds.right() - 1, top + ROW_HEIGHT - 1, 0xE02A323E);
            graphics.drawString(font, entry.label(), bounds.left() + 6, top + 5,
                    entry.dangerous() ? 0xFFFF9B9B : 0xFFFFFFFF, false);
        }
    }

    public static int rowAt(UiRect bounds, int rowCount, double mouseX, double mouseY) {
        if (!bounds.contains(mouseX, mouseY)) return -1;
        int row = ((int) mouseY - bounds.top()) / ROW_HEIGHT;
        return row >= 0 && row < rowCount ? row : -1;
    }
}
