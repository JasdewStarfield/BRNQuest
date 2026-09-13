package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import yourscraft.jasdewstarfield.brnquest.config.BrnQuestClientConfig;

/**
 * Computes the stable regions of the quest screen from one immutable snapshot.
 * Editor components consume these rectangles so opening a side drawer cannot
 * accidentally move either full-width toolbar.
 */
public record QuestScreenLayout(int width, int height, boolean navigationCollapsed, boolean detailsOpen) {
    private static final int COMPACT_WIDTH_THRESHOLD = 800;
    private static final int COMPACT_HEIGHT_THRESHOLD = 480;
    private static final int REGULAR_NAVIGATION_WIDTH = 132;
    private static final int REGULAR_DETAILS_WIDTH = 224;
    private static final int COMPACT_NAVIGATION_MIN = 100;
    private static final int COMPACT_NAVIGATION_MAX = 124;
    private static final int COMPACT_DETAILS_MIN = 184;
    private static final int COMPACT_DETAILS_MAX = 216;
    private static final int NAVIGATION_GAP = 4;
    private static final int NAVIGATION_HANDLE_WIDTH = 10;
    private static final int TOOLBAR_HEIGHT = 20;
    public static final int EDITOR_CONTROL_HEIGHT = 16;

    /** Minecraft Screen dimensions are already GUI-scaled, so physical resolution needs no special case. */
    public boolean compact() {
        return width < COMPACT_WIDTH_THRESHOLD || height < COMPACT_HEIGHT_THRESHOLD;
    }

    public int navigationWidth() {
        int custom = BrnQuestClientConfig.read(BrnQuestClientConfig.VALUES.navigationWidth);
        if (custom > 0) return clamp(custom, Math.min(100, width / 4), Math.max(100, width / 4));
        if (!compact()) return REGULAR_NAVIGATION_WIDTH;
        return clamp(Math.round(width * 0.22F), COMPACT_NAVIGATION_MIN, COMPACT_NAVIGATION_MAX);
    }

    public int navigationHandleWidth() {
        return NAVIGATION_HANDLE_WIDTH;
    }

    public int detailsWidth() {
        int custom = BrnQuestClientConfig.read(BrnQuestClientConfig.VALUES.detailsWidth);
        // Limit both custom drawers to leave room for the canvas when opened together.
        if (custom > 0) return clamp(custom, Math.min(184, width / 2), Math.max(184, width / 2));
        if (!compact()) return REGULAR_DETAILS_WIDTH;
        return clamp(Math.round(width * 0.34F), COMPACT_DETAILS_MIN, COMPACT_DETAILS_MAX);
    }

    public int topToolbarHeight() {
        return TOOLBAR_HEIGHT;
    }

    public int bottomToolbarHeight() {
        return TOOLBAR_HEIGHT;
    }

    public UiRect topToolbar() {
        return new UiRect(0, 0, width, topToolbarHeight());
    }

    public UiRect bottomToolbar() {
        return new UiRect(0, height - bottomToolbarHeight(), width, height);
    }

    public UiRect content() {
        return new UiRect(0, topToolbarHeight(), width, height - bottomToolbarHeight());
    }

    public int canvasLeft() {
        return canvasLeft(navigationCollapsed ? 0.0 : 1.0);
    }

    public int canvasRight() {
        return canvasRight(detailsOpen ? 1.0 : 0.0);
    }

    /** Animated left canvas edge; the navigation handle remains visible at zero progress. */
    public int canvasLeft(double navigationProgress) {
        int drawerWidth = navigationWidth() + NAVIGATION_GAP;
        return navigationHandleWidth() + (int) Math.round(drawerWidth * clampProgress(navigationProgress));
    }

    /** Animated right canvas edge shared with the visible leading edge of the details drawer. */
    public int canvasRight(double detailsProgress) {
        return width - (int) Math.round(detailsWidth() * clampProgress(detailsProgress));
    }

    /** Translation that keeps the navigation drawer's right edge attached to its moving handle. */
    public int navigationDrawerOffset(double navigationProgress) {
        int openHandleLeft = navigationWidth() + NAVIGATION_GAP;
        int animatedHandleLeft = canvasLeft(navigationProgress) - navigationHandleWidth();
        return animatedHandleLeft - openHandleLeft;
    }

    /** Translation that keeps the details drawer's left edge attached to the animated canvas edge. */
    public int detailsDrawerOffset(double detailsProgress) {
        return canvasRight(detailsProgress) - detailLeft();
    }

    public int detailLeft() {
        return width - detailsWidth();
    }

    public int contentCenterY() {
        return content().centerY();
    }

    public boolean isContentY(double y) {
        return y >= content().top() && y < content().bottom();
    }

    /** The fixed bottom bar is fully usable; side drawers end above it and must not constrain status text. */
    public int bottomStatusMaximumWidth(int leadingButtonLeft) {
        return Math.max(40, leadingButtonLeft - 8);
    }

    public UiRect centeredDialog(int preferredWidth, int minimumWidth, int horizontalMargin, int dialogHeight) {
        int dialogWidth = Math.min(preferredWidth, Math.max(minimumWidth, width - horizontalMargin * 2));
        int left = (width - dialogWidth) / 2;
        int top = Math.max(topToolbarHeight() + 8, (height - dialogHeight) / 2);
        return new UiRect(left, top, left + dialogWidth, top + dialogHeight);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double clampProgress(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
