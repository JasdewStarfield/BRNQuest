package yourscraft.jasdewstarfield.brnquest.client.ui;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Checks the actual emitted pixel cells, including odd stroke centers and the unobscured arrow tip. */
class QuestDependencyGeometryTest {
    @Test void focusedStrokesGrowSymmetricallyAroundTheOriginalPixelCenter() {
        var horizontal = route(100, 0);
        assertEquals(Set.of(0), crossSection(horizontal, false, true, 50));
        assertEquals(Set.of(-1, 0, 1), crossSection(horizontal, true, true, 50));
        var vertical = route(0, 100);
        assertEquals(Set.of(0), crossSection(vertical, false, false, 50));
        assertEquals(Set.of(-1, 0, 1), crossSection(vertical, true, false, 50));
    }

    @Test void allFourArrowTipsHaveTwoEmptyPixelsBeforeTheNodeOutline() {
        for (var center : List.of(point(100, 0), point(-100, 0), point(0, 100), point(0, -100))) {
            var path = route(center.x(), center.y());
            var tip = path.points().getLast();
            int distance = Math.abs(tip.x() - center.x()) + Math.abs(tip.y() - center.y());
            assertEquals(2, distance - 12 - 1, "Clearance counts complete empty pixel cells");
            for (boolean focused : new boolean[]{false, true}) {
                var crossSection = crossSection(path, focused, center.y() == 0,
                        center.y() == 0 ? tip.x() : tip.y());
                assertEquals(Set.of(0), crossSection, "A thick shaft cannot flatten the arrow's one-pixel point");
            }
        }
    }

    @Test void slightlyOffsetVerticalNeighborsEndAtTheChildCenter() {
        var path = route(8, 100);
        assertEquals(8, path.points().getLast().x());
        assertEquals(85, path.points().getLast().y());
        assertEquals(8, path.points().get(path.points().size() - 2).x());
        assertOrthogonal(path);
    }

    @Test void bentAndReversedPathsKeepBothEndpointOutlinesClear() {
        for (var center : List.of(point(34, 34), point(-34, 34), point(34, -34), point(-34, -34),
                point(100, 60), point(8, -100))) {
            var path = route(center.x(), center.y());
            assertOrthogonal(path);
            for (boolean focused : new boolean[]{false, true}) {
                for (var pixel : pixels(path, focused)) {
                    assertFalse(inNode(pixel, 0, 0, 12), "Parent rim must remain clear");
                    assertFalse(inNode(pixel, center.x(), center.y(), 12), "Child highlight must not hide the arrow");
                }
                var tip = path.points().getLast();
                var previous = path.points().get(path.points().size() - 2);
                boolean horizontal = tip.y() == previous.y();
                assertEquals(Set.of(horizontal ? tip.y() : tip.x()), crossSection(path, focused, horizontal,
                        horizontal ? tip.x() : tip.y()));
            }
        }
    }

    @Test void differentNodeSizesUseTheirOwnOutlineRadius() {
        var path = QuestDependencyGeometry.route(new QuestDependencyGeometry.Node(0, 0, 8),
                new QuestDependencyGeometry.Node(200, 0, 30));
        assertEquals(point(11, 0), path.points().getFirst());
        assertEquals(point(167, 0), path.points().getLast());
    }

    @Test void crampedNodesUseAnOutsideBendAndCoincidentNodesDoNotInventADirection() {
        var path = route(20, 8);
        assertOrthogonal(path);
        assertTrue(path.points().getLast().y() > 8 + 12);
        assertTrue(path.points().get(path.points().size() - 2).y() > path.points().getLast().y());
        assertTrue(pixels(route(0, 0), true).isEmpty());
    }

    @Test void onePixelCorridorDoesNotDivideByZeroOrDrawPastTheTip() {
        var path = route(31, 0);
        var tip = path.points().getLast();
        assertEquals(Set.of(0), crossSection(path, true, true, tip.x()));
        assertTrue(pixels(path, true).stream().allMatch(pixel -> pixel.x() >= 15 && pixel.x() <= 16));
        assertTrue(pixels(route(30, 0), true).isEmpty());
    }

    private static QuestDependencyGeometry.Path route(int x, int y) {
        return QuestDependencyGeometry.route(new QuestDependencyGeometry.Node(0, 0, 12),
                new QuestDependencyGeometry.Node(x, y, 12));
    }
    private static QuestDependencyGeometry.Point point(int x, int y) { return new QuestDependencyGeometry.Point(x, y); }
    private static Set<QuestDependencyGeometry.Point> pixels(QuestDependencyGeometry.Path path, boolean focused) {
        var result = new HashSet<QuestDependencyGeometry.Point>();
        QuestDependencyGeometry.paint(path, focused, (left, top, right, bottom) -> {
            for (int y = top; y < bottom; y++) for (int x = left; x < right; x++) result.add(point(x, y));
        });
        return result;
    }
    private static Set<Integer> crossSection(QuestDependencyGeometry.Path path, boolean focused, boolean horizontal, int axis) {
        var result = new HashSet<Integer>();
        for (var pixel : pixels(path, focused))
            if ((horizontal ? pixel.x() : pixel.y()) == axis) result.add(horizontal ? pixel.y() : pixel.x());
        return result;
    }
    private static boolean inNode(QuestDependencyGeometry.Point pixel, int x, int y, int radius) {
        return Math.abs(pixel.x() - x) <= radius && Math.abs(pixel.y() - y) <= radius;
    }
    private static void assertOrthogonal(QuestDependencyGeometry.Path path) {
        for (int index = 1; index < path.points().size(); index++) {
            var from = path.points().get(index - 1); var to = path.points().get(index);
            assertNotEquals(from, to);
            assertTrue(from.x() == to.x() || from.y() == to.y());
        }
    }
}
