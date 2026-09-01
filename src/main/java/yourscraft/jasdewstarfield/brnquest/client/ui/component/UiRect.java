package yourscraft.jasdewstarfield.brnquest.client.ui.component;

/** Immutable screen-space rectangle shared by layout, rendering and hit testing. */
public record UiRect(int left, int top, int right, int bottom) {
    public int width() {
        return Math.max(0, right - left);
    }

    public int height() {
        return Math.max(0, bottom - top);
    }

    public int centerX() {
        return left + width() / 2;
    }

    public int centerY() {
        return top + height() / 2;
    }

    public boolean contains(double x, double y) {
        return x >= left && x <= right && y >= top && y <= bottom;
    }

    /** Pixel-clipped surfaces exclude the right/bottom edge; legacy inclusive callers stay unchanged. */
    public boolean containsExclusive(double x, double y) {
        return x >= left && x < right && y >= top && y < bottom;
    }

    public UiRect intersection(UiRect other) {
        int x = Math.max(left, other.left), y = Math.max(top, other.top);
        return new UiRect(x, y, Math.max(x, Math.min(right, other.right)),
                Math.max(y, Math.min(bottom, other.bottom)));
    }

    /** Apply a drawer/local-to-screen offset once before sharing geometry with render and input. */
    public UiRect translated(int x, int y) {
        return new UiRect(left + x, top + y, right + x, bottom + y);
    }
}
