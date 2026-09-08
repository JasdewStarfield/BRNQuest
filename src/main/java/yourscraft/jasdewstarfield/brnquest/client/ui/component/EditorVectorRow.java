package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import java.util.List;
import java.util.ArrayList;

/** One property row with three axis labels/inputs and an optional direct-current action. */
public final class EditorVectorRow {
    private EditorVectorRow() {}
    public record Layout(List<UiRect> axes, UiRect current) {}
    public static Layout layout(UiRect row, boolean current) {
        int right = current ? row.right() - 22 : row.right();
        var axes = new ArrayList<UiRect>();
        for (int axis = 0; axis < 3; axis++) {
            int left = row.left() + (right - row.left()) * axis / 3;
            int end = row.left() + (right - row.left()) * (axis + 1) / 3;
            axes.add(new UiRect(left, row.top(), Math.max(left, end - 2), row.bottom()));
        }
        return new Layout(List.copyOf(axes), current ? new UiRect(right + 2, row.top(), row.right(), row.bottom()) : null);
    }
}
