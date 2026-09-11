package yourscraft.jasdewstarfield.brnquest.client.ui.component;

/**
 * Pure geometry for the ghost item selector. Keeping inventory index mapping here makes
 * rendering and hit testing share the same vanilla 9..35 main / 0..8 hotbar ordering.
 */
public record EditorItemSelectorLayout(int screenWidth, int screenHeight) {
    public static final int PANEL_WIDTH = 176;
    public static final int PANEL_HEIGHT = 166;
    public static final int SLOT_SIZE = 18;

    public UiRect panel() {
        int left = (screenWidth - PANEL_WIDTH) / 2;
        int top = Math.max(4, (screenHeight - PANEL_HEIGHT) / 2);
        return new UiRect(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT);
    }

    public UiRect targetSlot() {
        UiRect panel = panel();
        return slot(panel.right() - 31, panel.top() + 27);
    }

    public UiRect inventorySlot(int inventoryIndex) {
        if (inventoryIndex < 0 || inventoryIndex > 35) {
            throw new IllegalArgumentException("Player inventory index must be between 0 and 35");
        }
        UiRect panel = panel();
        int left = panel.left() + 7;
        if (inventoryIndex < 9) {
            return slot(left + inventoryIndex * SLOT_SIZE, panel.top() + 121);
        }
        int mainIndex = inventoryIndex - 9;
        return slot(left + mainIndex % 9 * SLOT_SIZE, panel.top() + 63 + mainIndex / 9 * SLOT_SIZE);
    }

    public int inventoryIndexAt(double mouseX, double mouseY) {
        for (int index = 0; index < 36; index++) {
            if (inventorySlot(index).containsExclusive(mouseX, mouseY)) return index;
        }
        return -1;
    }

    public UiRect cancelButton() {
        UiRect panel = panel();
        return new UiRect(panel.left() + 7, panel.bottom() - 20, panel.centerX() - 4, panel.bottom() - 4);
    }

    public UiRect doneButton() {
        UiRect panel = panel();
        return new UiRect(panel.centerX() + 4, panel.bottom() - 20, panel.right() - 7, panel.bottom() - 4);
    }

    private static UiRect slot(int left, int top) {
        return new UiRect(left, top, left + SLOT_SIZE, top + SLOT_SIZE);
    }
}
