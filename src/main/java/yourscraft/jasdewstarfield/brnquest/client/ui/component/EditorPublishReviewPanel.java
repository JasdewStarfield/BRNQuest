package yourscraft.jasdewstarfield.brnquest.client.ui.component;

/** Pure geometry for the scrollable server-authoritative publish review overlay. */
public final class EditorPublishReviewPanel {
    public static final int ROW_HEIGHT = 28;

    private EditorPublishReviewPanel() {}

    public static Layout layout(QuestScreenLayout screen) {
        int maximumHeight = Math.max(180, screen.content().height() - 16);
        int height = Math.min(360, maximumHeight);
        UiRect panel = screen.centeredDialog(520, 300, 20, height);
        UiRect list = new UiRect(panel.left() + 12, panel.top() + 92,
                panel.right() - 12, panel.bottom() - 38);
        int buttonWidth = Math.min(150, Math.max(90, (panel.width() - 36) / 2));
        UiRect cancel = new UiRect(panel.left() + 12, panel.bottom() - 28,
                panel.left() + 12 + buttonWidth, panel.bottom() - 10);
        UiRect confirm = new UiRect(panel.right() - 12 - buttonWidth, panel.bottom() - 28,
                panel.right() - 12, panel.bottom() - 10);
        return new Layout(panel, list, cancel, confirm);
    }

    public static int visibleRows(Layout layout) {
        return Math.max(1, layout.list().height() / ROW_HEIGHT);
    }

    public static int maximumScroll(Layout layout, int rowCount) {
        return Math.max(0, rowCount - visibleRows(layout));
    }

    public static int rowAt(Layout layout, int scroll, int rowCount, double x, double y) {
        if (!layout.list().contains(x, y)) return -1;
        int visibleIndex = ((int) y - layout.list().top()) / ROW_HEIGHT;
        int index = scroll + visibleIndex;
        return visibleIndex < visibleRows(layout) && index < rowCount ? index : -1;
    }

    public record Layout(UiRect panel, UiRect list, UiRect cancel, UiRect confirm) {}
}
