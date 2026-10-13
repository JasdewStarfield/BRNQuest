package yourscraft.jasdewstarfield.brnquest.client.ui.component;

/** One rendered widget position, with its viewport intersection shared by paint, hover and clicks. */
public record EditorWidgetFrame(UiRect bounds, UiRect visible) {
    public static EditorWidgetFrame scrolled(UiRect authored, UiRect viewport, int scroll) {
        UiRect bounds = authored.translated(0, -scroll);
        return new EditorWidgetFrame(bounds, bounds.intersection(viewport));
    }

    public boolean shown() { return visible.width() > 0 && visible.height() > 0; }
    public boolean contains(double x, double y) { return shown() && visible.containsExclusive(x, y); }

    /** Keyboard focus reveals the entire control; pointer hover can remain on a partially visible control. */
    public static int reveal(int scroll, UiRect authored, UiRect viewport) {
        int top = authored.top() - scroll, bottom = authored.bottom() - scroll;
        if (top < viewport.top()) return Math.max(0, authored.top() - viewport.top());
        if (bottom > viewport.bottom()) return Math.max(0, authored.bottom() - viewport.bottom());
        return scroll;
    }
}
