package yourscraft.jasdewstarfield.brnquest.client.ui.component;

/**
 * Shared geometry for the compound quest-icon row. Rendering and pointer input retain this
 * exact layout so adding another property row cannot separate the visible controls from hit testing.
 */
public final class QuestIconEditorRow {
    public record Layout(UiRect label, UiRect mode, UiRect input, UiRect picker) {}

    private QuestIconEditorRow() {}

    public static Layout layout(int left, int top, int width, int labelWidth) {
        EditorPropertyFormLayout.Row row = EditorPropertyFormLayout.row(left, top, width, labelWidth);
        UiRect mode = new UiRect(row.field().left(), row.field().top(), row.field().left() + 42,
                row.field().bottom());
        UiRect picker = new UiRect(row.field().right() - 20, row.field().top(), row.field().right(),
                row.field().bottom());
        UiRect input = new UiRect(mode.right() + 3, row.field().top(), picker.left() - 2,
                row.field().bottom());
        return new Layout(row.label(), mode, input, picker);
    }
}
