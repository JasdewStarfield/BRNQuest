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

    /** Calculates only visible rows; key lookup and expensive row presentation are never eager for the whole list. */
    public Frame<K> advance(UiRect bounds, UiRect screenClip, int trackX, int rowHeight, int rowGap,
                            int count, IntFunction<K> keyAt, double seconds, double speed) {
        if (rowHeight <= 0 || rowGap < 0 || rowGap >= rowHeight || count < 0) {
            throw new IllegalArgumentException("Invalid list metrics");
        }
        int contentHeight = Math.multiplyExact(count, rowHeight);
        int offset = (int) Math.round(scroll.advanceFrame(contentHeight, bounds.height(), seconds, speed));
        UiRect viewport = bounds.intersection(screenClip);
        UiRect track = new UiRect(trackX, bounds.top(), trackX + 3, bounds.bottom()).intersection(screenClip);
        List<Row<K>> rows = new ArrayList<>();
        if (viewport.width() > 0 && viewport.height() > 0) {
            int first = Math.max(0, (viewport.top() - bounds.top() + offset) / rowHeight);
            for (int i = first; i < count; i++) {
                int y = bounds.top() + i * rowHeight - offset;
                if (y >= viewport.bottom()) break;
                UiRect row = new UiRect(bounds.left(), y, bounds.right(), y + rowHeight - rowGap);
                UiRect visible = row.intersection(viewport);
                if (visible.height() > 0) rows.add(new Row<>(keyAt.apply(i), i, row, visible));
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
            for (Row<K> row : frame.rows()) rowRenderer.accept(row);
        } finally {
            graphics.disableScissor();
        }
    }

    public Optional<Row<K>> rowAt(double x, double y) {
        return frame == null ? Optional.empty() : frame.rowAt(x, y);
    }

    public boolean mouseScrolled(double x, double y, double delta, double step) {
        if (frame == null || !(frame.viewport().containsExclusive(x, y) || frame.track().containsExclusive(x, y))) return false;
        scroll.scrollWheel(delta, step, frame.contentHeight(), frame.bounds().height());
        return true;
    }

    public boolean mouseClicked(double x, double y, int button) {
        if (frame == null || button != 0 || frame.contentHeight() <= frame.bounds().height()
                || !frame.track().containsExclusive(x, y)) return false;
        scroll.snapFromTrack(y, frame.bounds().top(), frame.bounds().bottom(),
                frame.contentHeight(), frame.bounds().height());
        // The clicked track updates the next frame; the old displayed row positions remain authoritative until then.
        return true;
    }

    public void reset() {
        scroll.snap(0);
        invalidate();
    }

    /** Discards old input geometry after resize/data replacement without losing the scroll position. */
    public void invalidate() { frame = null; }
}
