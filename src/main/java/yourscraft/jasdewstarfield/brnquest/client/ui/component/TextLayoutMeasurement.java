package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import java.util.List;

/** Pure geometry for diagnostics; all rectangles and sizes use GUI pixels after pose transforms. */
public record TextLayoutMeasurement(String text, String source, UiRect drawn, UiRect available,
                                    double requiredWidth, double requiredHeight, double renderedWidth,
                                    double renderedHeight, double scaleX, double scaleY,
                                    boolean truncated, boolean wrapped, boolean knownSlot) {
    public double widthOverflow() { return Math.max(0, requiredWidth - available.width()); }
    public double heightOverflow() { return Math.max(0, requiredHeight - available.height()); }

    /** A screen boundary is useful for clipping, but cannot prove that a label fits its control. */
    public boolean overflow() {
        return knownSlot && (widthOverflow() > 0.01 || heightOverflow() > 0.01);
    }

    public boolean clipped() {
        return drawn.left() < available.left() || drawn.top() < available.top()
                || drawn.right() > available.right() || drawn.bottom() > available.bottom();
    }

    public UiRect hoverBounds() { return drawn.intersection(available); }

    /** Explicit slots remain hoverable when truncation removes every character. */
    public boolean hovered(double x, double y) {
        return hoverBounds().containsExclusive(x, y)
                || knownSlot && available.containsExclusive(x, y);
    }

    public double occupancyPercent() {
        return available.width() == 0 ? Double.POSITIVE_INFINITY : 100 * requiredWidth / available.width();
    }

    /** Painted text wins over another label's empty slot; overlapping text follows the last draw order. */
    public static TextLayoutMeasurement pick(List<TextLayoutMeasurement> entries, double x, double y) {
        for (int i = entries.size() - 1; i >= 0; i--)
            if (entries.get(i).hoverBounds().containsExclusive(x, y)) return entries.get(i);
        for (int i = entries.size() - 1; i >= 0; i--)
            if (entries.get(i).hovered(x, y)) return entries.get(i);
        return null;
    }
}
