package yourscraft.jasdewstarfield.brnquest.client.ui.component;

/**
 * Defines the shared vertical viewport used by list rendering and pointer hit testing.
 * The half-row factory intentionally leaves a partial next entry visible as a scrolling cue.
 */
public record EditorListViewport(int top, int bottom) {
    /** Uses the largest whole-plus-half-row height that fits before {@code maximumBottom}. */
    public static EditorListViewport withHalfRowPreview(int top, int maximumBottom, int rowHeight) {
        int safeRowHeight = Math.max(1, rowHeight);
        int availableHeight = Math.max(0, maximumBottom - top);
        int previewHeight = Math.max(1, safeRowHeight / 2);
        int wholeRows = Math.max(0, (availableHeight - previewHeight) / safeRowHeight);
        int viewportHeight = wholeRows * safeRowHeight + previewHeight;
        return new EditorListViewport(top, top + Math.min(availableHeight, viewportHeight));
    }

    public int height() {
        return Math.max(0, bottom - top);
    }

    /** The lower edge is exclusive, matching GUI scissor behavior. */
    public boolean containsY(double y) {
        return y >= top && y < bottom;
    }

    /** Returns whether any visible vertical portion of a row intersects this viewport. */
    public boolean intersects(UiRect bounds) {
        return bounds.bottom() > top && bounds.top() < bottom;
    }
}
