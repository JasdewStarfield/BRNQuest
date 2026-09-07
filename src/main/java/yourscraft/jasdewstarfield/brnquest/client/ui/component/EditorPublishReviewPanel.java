package yourscraft.jasdewstarfield.brnquest.client.ui.component;

/** Pure geometry for the scrollable server-authoritative publish review overlay. */
public final class EditorPublishReviewPanel {
    public static final int ROW_HEIGHT = 28;

    private EditorPublishReviewPanel() {}

    public static Layout layout(QuestScreenLayout screen) {
        int maximumHeight = Math.max(180, screen.content().height() - 16);
        int height = Math.min(360, maximumHeight);
        UiRect panel = screen.centeredDialog(520, 300, 20, height);
        UiRect filters = new UiRect(panel.left() + 12, panel.top() + 82,
                panel.right() - 12, panel.top() + 100);
        UiRect list = new UiRect(panel.left() + 12, panel.top() + 104,
                panel.right() - 12, panel.bottom() - 38);
        int buttonWidth = Math.min(150, Math.max(90, (panel.width() - 36) / 2));
        UiRect cancel = new UiRect(panel.left() + 12, panel.bottom() - 28,
                panel.left() + 12 + buttonWidth, panel.bottom() - 10);
        UiRect confirm = new UiRect(panel.right() - 12 - buttonWidth, panel.bottom() - 28,
                panel.right() - 12, panel.bottom() - 10);
        return new Layout(panel, filters, list, cancel, confirm);
    }

    public static int visibleRows(Layout layout) {
        return Math.max(1, layout.list().height() / ROW_HEIGHT);
    }

    /** Counts every intersecting row, including simultaneous partial rows at the top and bottom. */
    public static int renderedRows(Layout layout, int rowOffset) {
        int coveredHeight = Math.max(0, layout.list().height() - Math.min(0, rowOffset));
        return Math.max(1, (coveredHeight + ROW_HEIGHT - 1) / ROW_HEIGHT);
    }

    public static int maximumScroll(Layout layout, int rowCount) {
        return Math.max(0, rowCount - visibleRows(layout));
    }

    public static int rowAt(Layout layout, EditorSmoothScroll scroll, int rowCount, double x, double y) {
        if (!layout.list().contains(x, y)) return -1;
        return scroll.rowAt(y, layout.list().top(), layout.list().bottom(), ROW_HEIGHT, rowCount);
    }

    public static UiRect filterBounds(Layout layout, int index, int count) {
        if (index < 0 || index >= count || count <= 0) throw new IllegalArgumentException("Invalid filter index");
        int gap = 3;
        int width = Math.max(1, (layout.filters().width() - gap * (count - 1)) / count);
        int left = layout.filters().left() + index * (width + gap);
        int right = index == count - 1 ? layout.filters().right() : left + width;
        return new UiRect(left, layout.filters().top(), right, layout.filters().bottom());
    }

    public static int filterAt(Layout layout, int count, double x, double y) {
        for (int i = 0; i < count; i++) if (filterBounds(layout, i, count).containsExclusive(x, y)) return i;
        return -1;
    }

    public record Layout(UiRect panel, UiRect filters, UiRect list, UiRect cancel, UiRect confirm) {}
}
