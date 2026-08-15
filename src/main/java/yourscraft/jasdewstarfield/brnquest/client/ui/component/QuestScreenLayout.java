package yourscraft.jasdewstarfield.brnquest.client.ui.component;

/**
 * Computes the stable regions of the quest screen from one immutable snapshot.
 * Editor components consume these rectangles so opening a side drawer cannot
 * accidentally move either full-width toolbar.
 */
public record QuestScreenLayout(int width, int height, boolean navigationCollapsed, boolean detailsOpen) {
    public static final int NAVIGATION_WIDTH = 150;
    public static final int NAVIGATION_HANDLE_WIDTH = 12;
    public static final int DETAILS_WIDTH = 250;
    public static final int TOP_TOOLBAR_HEIGHT = 24;
    public static final int BOTTOM_TOOLBAR_HEIGHT = 24;
    public static final int EDITOR_CONTROL_HEIGHT = 16;

    public UiRect topToolbar() {
        return new UiRect(0, 0, width, TOP_TOOLBAR_HEIGHT);
    }

    public UiRect bottomToolbar() {
        return new UiRect(0, height - BOTTOM_TOOLBAR_HEIGHT, width, height);
    }

    public UiRect content() {
        return new UiRect(0, TOP_TOOLBAR_HEIGHT, width, height - BOTTOM_TOOLBAR_HEIGHT);
    }

    public int canvasLeft() {
        return navigationCollapsed ? NAVIGATION_HANDLE_WIDTH
                : NAVIGATION_WIDTH + 4 + NAVIGATION_HANDLE_WIDTH;
    }

    public int canvasRight() {
        return detailsOpen ? detailLeft() : width;
    }

    public int detailLeft() {
        return width - DETAILS_WIDTH;
    }

    public int contentCenterY() {
        return content().centerY();
    }

    public boolean isContentY(double y) {
        return y >= content().top() && y < content().bottom();
    }

    public UiRect centeredDialog(int preferredWidth, int minimumWidth, int horizontalMargin, int dialogHeight) {
        int dialogWidth = Math.min(preferredWidth, Math.max(minimumWidth, width - horizontalMargin * 2));
        int left = (width - dialogWidth) / 2;
        int top = Math.max(TOP_TOOLBAR_HEIGHT + 8, (height - dialogHeight) / 2);
        return new UiRect(left, top, left + dialogWidth, top + dialogHeight);
    }
}
