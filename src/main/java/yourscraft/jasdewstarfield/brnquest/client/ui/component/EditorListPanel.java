package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * Composable fixed-height list: owns scroll, clipping and hit geometry, but knows nothing about
 * quests, networking or row presentation. All rectangles are GUI screen coordinates, with no
 * caller pose translation. A screen renders once, then routes input through that exact frame.
 */
public final class EditorListPanel<K> {
    public record Row<K>(K key, int index, UiRect bounds, UiRect visible) {
        public UiRect clip(UiRect child) { return visible.intersection(child); }
    }

    public record Frame<K>(UiRect bounds, UiRect viewport, UiRect track, int contentHeight,
                           int pixelScroll, List<Row<K>> rows) {
        public Frame { rows = List.copyOf(rows); }

        public Optional<Row<K>> rowAt(double x, double y) {
            return rows.stream().filter(row -> row.visible().containsExclusive(x, y)).findFirst();
        }
    }

    private final EditorSmoothScroll scroll = new EditorSmoothScroll();
    private Frame<K> frame;
    private int focusedIndex = -1, itemCount, columns = 1, rowHeight;
    private K focusedKey;
    private IntFunction<K> keys;

    /** Calculates only visible rows; key lookup and expensive row presentation are never eager for the whole list. */
    public Frame<K> advance(UiRect bounds, UiRect screenClip, int trackX, int rowHeight, int rowGap,
                            int count, IntFunction<K> keyAt, double seconds, double speed) {
        return advanceGrid(bounds, screenClip, trackX, rowHeight, rowGap, 1, 0, count, keyAt, seconds, speed);
    }

    /** Row-major grid sharing the same visible-cell geometry for paint, clicks and tooltips. */
    public Frame<K> advanceGrid(UiRect bounds, UiRect screenClip, int trackX, int rowHeight, int rowGap,
                            int columns, int columnGap, int count, IntFunction<K> keyAt, double seconds, double speed) {
        if (rowHeight <= 0 || rowGap < 0 || rowGap >= rowHeight || count < 0 || columns < 1 || columnGap < 0) {
            throw new IllegalArgumentException("Invalid list metrics");
        }
        // Vertical clipping changes usable scroll height, not just drawing: keep the last row reachable.
        UiRect clipped = bounds.intersection(screenClip);
        bounds = new UiRect(bounds.left(), clipped.top(), bounds.right(), clipped.bottom());
        int rowCount = count / columns + (count % columns == 0 ? 0 : 1);
        int contentHeight = Math.multiplyExact(rowCount, rowHeight);
        this.itemCount = count; this.columns = columns; this.rowHeight = rowHeight;
        // A changed source must never leave an old index pointing at a different candidate.
        if (focusedIndex >= count || (focusedIndex >= 0 && !java.util.Objects.equals(focusedKey, keyAt.apply(focusedIndex))))
            clearFocus();
        if (focusedIndex >= 0) focusedKey = keyAt.apply(focusedIndex);
        keys = keyAt;
        int offset = (int) Math.round(scroll.advanceFrame(contentHeight, bounds.height(), seconds, speed));
        UiRect viewport = bounds.intersection(screenClip);
        UiRect track = new UiRect(trackX, bounds.top(), trackX + 3, bounds.bottom()).intersection(screenClip);
        List<Row<K>> rows = new ArrayList<>();
        if (viewport.width() > 0 && viewport.height() > 0) {
            int first = Math.max(0, (viewport.top() - bounds.top() + offset) / rowHeight);
            int availableWidth = Math.max(0, bounds.width() - (columns - 1) * columnGap);
            for (int rowIndex = first; rowIndex < rowCount; rowIndex++) {
                int y = bounds.top() + rowIndex * rowHeight - offset;
                if (y >= viewport.bottom()) break;
                for (int col = 0; col < columns; col++) {
                    int i = rowIndex * columns + col;
                    if (i >= count) break; // An odd last row has no phantom clickable cell.
                    int left = bounds.left() + availableWidth * col / columns + col * columnGap;
                    int right = bounds.left() + availableWidth * (col + 1) / columns + col * columnGap;
                    UiRect row = new UiRect(left, y, right, y + rowHeight - rowGap);
                    UiRect visible = row.intersection(viewport);
                    if (visible.height() > 0 && visible.width() > 0)
                        rows.add(new Row<>(keyAt.apply(i), i, row, visible));
                }
            }
        }
        frame = new Frame<>(bounds, viewport, track, contentHeight, offset, rows);
        return frame;
    }

    /** Draws the most recently advanced frame without advancing animation a second time. */
    public void render(GuiGraphics graphics, Consumer<Row<K>> rowRenderer, Runnable emptyRenderer) {
        if (frame == null) return;
        UiRect track = frame.track();
        if (track.width() > 0 && track.height() > 0) {
            // Even a window shorter than the minimum scrollbar thumb must not draw into the footer.
            graphics.enableScissor(track.left(), track.top(), track.right(), track.bottom());
            try {
                EditorScrollbar.render(graphics, track.left(), frame.bounds().top(), frame.bounds().bottom(),
                        frame.contentHeight(), frame.bounds().height(), scroll.visual());
            } finally {
                graphics.disableScissor();
            }
        }
        UiRect clip = frame.viewport();
        if (clip.width() == 0 || clip.height() == 0) return;
        graphics.enableScissor(clip.left(), clip.top(), clip.right(), clip.bottom());
        try {
            if (frame.contentHeight() == 0) emptyRenderer.run();
            for (Row<K> row : frame.rows()) {
                rowRenderer.accept(row);
                if (row.index() == focusedIndex) graphics.renderOutline(row.bounds().left(), row.bounds().top(),
                        row.bounds().width(), row.bounds().height(), GraystonePalette.ACCENT);
            }
        } finally {
            graphics.disableScissor();
        }
    }

    public Optional<Row<K>> rowAt(double x, double y) {
        return frame == null ? Optional.empty() : frame.rowAt(x, y);
    }

    public boolean mouseScrolled(double x, double y, double delta, double step) {
        if (frame == null || !(frame.viewport().containsExclusive(x, y) || frame.track().containsExclusive(x, y))) return false;
        clearFocus();
        scroll.scrollWheel(delta, step, frame.contentHeight(), frame.bounds().height());
        return true;
    }

    public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && frame != null && frame.viewport().containsExclusive(x,y)) clearFocus();
        if (frame == null || button != 0 || frame.contentHeight() <= frame.bounds().height()
                || !frame.track().containsExclusive(x, y)) return false;
        scroll.snapFromTrack(y, frame.bounds().top(), frame.bounds().bottom(),
                frame.contentHeight(), frame.bounds().height());
        // The clicked track updates the next frame; the old displayed row positions remain authoritative until then.
        return true;
    }

    public void reset() {
        scroll.snap(0);
        clearFocus();
        invalidate();
    }

    /** Discards old input geometry after resize/data replacement without losing the scroll position. */
    public void invalidate() { frame = null; }

    public void clearFocus() { focusedIndex = -1; focusedKey = null; }
    public boolean hasFocus() { return focusedIndex >= 0; }

    /** Only a displayed focused row may activate; a scroll jump must render before it can be clicked. */
    public Optional<Row<K>> focusedRow() {
        return frame == null ? Optional.empty() : frame.rows().stream()
                .filter(row -> row.index() == focusedIndex && java.util.Objects.equals(row.key(), focusedKey)).findFirst();
    }

    /** GLFW navigation keys, row-major in grids. Enter is dispatched by the owning screen. */
    public boolean navigate(int key, boolean backwards) {
        if (frame == null || itemCount == 0) return false;
        int step = Math.max(1, frame.bounds().height() / rowHeight) * columns;
        int next = focusedIndex;
        switch (key) {
            case 258 -> next = focusedIndex < 0 ? (backwards ? itemCount - 1 : 0)
                    : Math.floorMod(focusedIndex + (backwards ? -1 : 1), itemCount);
            case 262 -> next = focusedIndex < 0 ? 0 : focusedIndex + (focusedIndex % columns < columns - 1 ? 1 : 0);
            case 263 -> next = focusedIndex < 0 ? 0 : focusedIndex - (focusedIndex % columns > 0 ? 1 : 0);
            case 264 -> next = focusedIndex < 0 ? 0 : focusedIndex + columns;
            case 265 -> next = focusedIndex < 0 ? itemCount - 1 : focusedIndex - columns;
            case 266 -> next = focusedIndex < 0 ? 0 : focusedIndex - step;
            case 267 -> next = focusedIndex < 0 ? itemCount - 1 : focusedIndex + step;
            case 268 -> next = 0;
            case 269 -> next = itemCount - 1;
            default -> { return false; }
        }
        focusedIndex = Math.max(0, Math.min(itemCount - 1, next));
        // Resolve one identity; activation below still requires it to exist in the displayed frame.
        focusedKey = keys.apply(focusedIndex);

        int top = focusedIndex / columns * rowHeight;
        int offset = frame.pixelScroll();
        if (top < offset) scroll.snap(top);
        else if (top + rowHeight > offset + frame.bounds().height())
            scroll.snap(Math.max(0, top + rowHeight - frame.bounds().height()));
        return true;
    }
}
