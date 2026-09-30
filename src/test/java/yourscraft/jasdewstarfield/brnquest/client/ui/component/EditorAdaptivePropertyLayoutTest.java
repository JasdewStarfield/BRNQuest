package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Reproduce long property rows followed by actions, including their scroll and native-input geometry. */
class EditorAdaptivePropertyLayoutTest {
    private static final EditorPropertyPanel.RowContent EMPTY = (g, x, y, w) -> {};
    private final EditorPropertyPanel.Layout layout = new EditorPropertyPanel.Layout(
            new UiRect(424, 20, 640, 354), 434, 192, 27, 40, 22);

    @Test void threeLineLabelMovesFollowingRowsAndKeepsTheirGap() {
        var rows = EditorPropertyPanel.rows(layout, List.of(EMPTY,
                EditorPropertyPanel.sized(w -> 27, EMPTY), EMPTY));
        assertEquals(27, rows.get(1).height());
        assertEquals(4, rows.get(2).top() - rows.get(1).bottom());
        assertTrue(rows.get(2).top() > layout.row(2).top());
        assertFalse(rows.get(1).containsExclusive(rows.get(2).centerX(), rows.get(2).centerY()));
    }

    @Test void nativeFieldStaysCenteredAndNormalHeightBesideATallLabel() {
        var row = EditorPropertyFormLayout.row(434, 62, 192, 68, 36);
        assertEquals(36, row.label().height());
        assertEquals(18, row.field().height());
        assertEquals(row.label().centerY(), row.field().centerY());
        assertEquals(502, row.field().left());
    }

    @Test void stackedActionUsesFullWidthAndCannotOverlapItsLabel() {
        var row = EditorPropertyFormLayout.stacked(434, 62, 192, 18, 22);
        assertEquals(192, row.field().width());
        assertEquals(row.label().left(), row.field().left());
        assertEquals(3, row.field().top() - row.label().bottom());
    }

    @Test void offscreenRowsRemainInTheMeasuredScrollRange() {
        var rows = EditorPropertyPanel.rows(layout, List.of(
                EditorPropertyPanel.sized(w -> 90, EMPTY), EMPTY, EMPTY));
        assertEquals(90 + 4 + 18 + 4 + 18, rows.getLast().bottom() - layout.firstRowY());
        var visibleInput = rows.getLast().translated(0, -80);
        assertEquals(rows.getLast().height(), visibleInput.height());
        assertFalse(visibleInput.containsExclusive(rows.getLast().centerX(), rows.getLast().centerY()));
    }

    @Test void dependencyFooterActionsAreAlignedAndHaveSpaceForFullNames() {
        for (int width : new int[]{480, 640, 900}) {
            var screen = new QuestScreenLayout(width, 374, false, true);
            var add = screen.dependencyFooterButton(0);
            var done = screen.dependencyFooterButton(1);
            assertEquals(add.left(), done.left());
            assertEquals(add.right(), done.right());
            assertTrue(add.width() >= 164);
            assertEquals(3, done.top() - add.bottom());
            assertTrue(done.bottom() < screen.bottomToolbar().top());
            assertFalse(add.containsExclusive(done.centerX(), done.centerY()));
        }
    }
}
