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
}
