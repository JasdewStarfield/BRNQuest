package yourscraft.jasdewstarfield.brnquest.client.ui.document;

import java.net.URI;
import java.util.List;

/** Immutable geometry consumed by both drawing and hit testing. Coordinates are document-local. */
public record DocumentLayout(List<Line> lines, List<LinkHit> links, int contentHeight, int width) {
    public DocumentLayout {
        lines = List.copyOf(lines);
        links = List.copyOf(links);
        contentHeight = Math.max(0, contentHeight);
        width = Math.max(1, width);
    }

    public record Bounds(int left, int top, int right, int bottom) {
        public Bounds {
            if (right < left || bottom < top) throw new IllegalArgumentException("Inverted document bounds");
        }

        public boolean contains(int x, int y) {
            return x >= left && x < right && y >= top && y < bottom;
        }

        public boolean intersects(Bounds other) {
            return left < other.right && right > other.left && top < other.bottom && bottom > other.top;
        }

        public int width() { return right - left; }
        public int height() { return bottom - top; }
    }

    public record Run(String text, DocumentTextStyle style, Bounds bounds, int baseline) {}

    public record Line(int baseline, Bounds bounds, List<Run> runs) {
        public Line { runs = List.copyOf(runs); }
    }

    public record LinkHit(URI destination, Bounds bounds) {}
}
