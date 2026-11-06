package yourscraft.jasdewstarfield.brnquest.client.ui;

import java.util.ArrayList;
import java.util.List;

/** Pixel geometry shared by normal and focused dependencies, independent of drawing and node state. */
final class QuestDependencyGeometry {
    private static final int NODE_GAP = 2;
    private static final int ARROW_LENGTH = 4;
    private static final int ARROW_HALF_WIDTH = 2;

    record Node(int x, int y, int radius) {}
    record Point(int x, int y) {}
    record Path(List<Point> points) {
        Path {
            var distinct = new ArrayList<Point>();
            for (var point : points)
                if (distinct.isEmpty() || !distinct.getLast().equals(point)) distinct.add(point);
            points = List.copyOf(distinct);
        }
    }
    @FunctionalInterface interface Pixels { void fill(int left, int top, int right, int bottom); }

    private QuestDependencyGeometry() {}

    /** Outside bends and their arrow/stroke pixels may enter the viewport before either node does. */
    static int cullingMargin(int radius) {
        return radius + 2 * NODE_GAP + ARROW_LENGTH + ARROW_HALF_WIDTH + 1;
    }

    /** Reserve the complete outer decoration and two empty pixel cells on both ends of the path. */
    static Path route(Node parent, Node child) {
        int dx = child.x() - parent.x(), dy = child.y() - parent.y();
        if (dx == 0 && dy == 0) return new Path(List.of());
        int from = parent.radius() + NODE_GAP + 1;
        int to = child.radius() + NODE_GAP + 1;
        if (Math.abs(dx) >= from + to && (dy == 0 || Math.abs(dx) - from - to >= ARROW_LENGTH)) {
            int direction = Integer.signum(dx);
            int start = parent.x() + direction * from, end = child.x() - direction * to;
            if (dy == 0) return new Path(List.of(new Point(start, parent.y()), new Point(end, child.y())));
            int middle = Math.floorDiv(start + end, 2);
            return new Path(List.of(new Point(start, parent.y()), new Point(middle, parent.y()),
                    new Point(middle, child.y()), new Point(end, child.y())));
        }
        if (Math.abs(dy) >= from + to && (dx == 0 || Math.abs(dy) - from - to >= ARROW_LENGTH)) {
            int direction = Integer.signum(dy);
            int start = parent.y() + direction * from, end = child.y() - direction * to;
            if (dx == 0) return new Path(List.of(new Point(parent.x(), start), new Point(child.x(), end)));
            int middle = Math.floorDiv(start + end, 2);
            return new Path(List.of(new Point(parent.x(), start), new Point(parent.x(), middle),
                    new Point(child.x(), middle), new Point(child.x(), end)));
        }
        // Close or enlarged nodes have no facing corridor. Route outside instead of reversing the arrow.
        if (Math.abs(dx) >= Math.abs(dy)) {
            int side = dy > 0 ? 1 : -1;
            int start = parent.y() + side * from, end = child.y() + side * to;
            int outside = side > 0 ? Math.max(start, end) + ARROW_LENGTH + NODE_GAP
                    : Math.min(start, end) - ARROW_LENGTH - NODE_GAP;
            return new Path(List.of(new Point(parent.x(), start), new Point(parent.x(), outside),
                    new Point(child.x(), outside), new Point(child.x(), end)));
        }
        int side = dx > 0 ? 1 : -1;
        int start = parent.x() + side * from, end = child.x() + side * to;
        int outside = side > 0 ? Math.max(start, end) + ARROW_LENGTH + NODE_GAP
                : Math.min(start, end) - ARROW_LENGTH - NODE_GAP;
        return new Path(List.of(new Point(start, parent.y()), new Point(outside, parent.y()),
                new Point(outside, child.y()), new Point(end, child.y())));
    }

    /** Odd widths share the same pixel center; the shaft stops at the arrow base so its tip stays pointed. */
    static void paint(Path path, boolean focused, Pixels pixels) {
        int width = focused ? 3 : 1;
        var points = path.points();
        for (int index = 1; index < points.size(); index++) {
            var start = points.get(index - 1);
            var end = points.get(index);
            if (index < points.size() - 1) {
                segment(start, end, width, pixels);
                continue;
            }
            int dx = Integer.signum(end.x() - start.x()), dy = Integer.signum(end.y() - start.y());
            int length = Math.min(ARROW_LENGTH, Math.abs(end.x() - start.x()) + Math.abs(end.y() - start.y()));
            var base = new Point(end.x() - dx * length, end.y() - dy * length);
            if (!start.equals(base)) segment(start, base, width, pixels);
            // Short final segments use a smaller head that fits between the elbow and the node.
            int halfWidth = Math.min(ARROW_HALF_WIDTH, Math.max(1, length / 2));
            for (int offset = 0; offset <= length; offset++) {
                int half = halfWidth * offset / length;
                int x = end.x() - dx * offset, y = end.y() - dy * offset;
                if (dx != 0) pixels.fill(x, y - half, x + 1, y + half + 1);
                else pixels.fill(x - half, y, x + half + 1, y + 1);
            }
        }
    }

    private static void segment(Point start, Point end, int width, Pixels pixels) {
        int half = width / 2;
        if (start.y() == end.y()) pixels.fill(Math.min(start.x(), end.x()), start.y() - half,
                Math.max(start.x(), end.x()) + 1, start.y() + half + 1);
        else pixels.fill(start.x() - half, Math.min(start.y(), end.y()), start.x() + half + 1,
                Math.max(start.y(), end.y()) + 1);
    }
}
