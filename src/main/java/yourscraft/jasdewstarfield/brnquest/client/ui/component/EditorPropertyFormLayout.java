package yourscraft.jasdewstarfield.brnquest.client.ui.component;

/** Shared labeled-row geometry for quest, task, reward, and descriptor-driven editor forms. */
public final class EditorPropertyFormLayout {
    public static final int FIELD_HEIGHT = 18;

    public record Row(UiRect label, UiRect field) {}

    private EditorPropertyFormLayout() {}

    public static Row row(int left, int top, int width, int labelWidth) {
        return row(left, top, width, labelWidth, FIELD_HEIGHT);
    }

    /** Tall labels reserve their full height; native inputs remain 18px high and vertically centered. */
    public static Row row(int left, int top, int width, int labelWidth, int height) {
        int safeLabelWidth = Math.max(0, Math.min(labelWidth, width));
        int rowHeight = Math.max(FIELD_HEIGHT, height);
        int fieldTop = top + (rowHeight - FIELD_HEIGHT) / 2;
        return new Row(new UiRect(left, top, left + safeLabelWidth, top + rowHeight),
                new UiRect(left + safeLabelWidth, fieldTop, left + width, fieldTop + FIELD_HEIGHT));
    }

    /** Full-width actions sit below their label when a side-by-side row cannot hold the complete action. */
    public static Row stacked(int left, int top, int width, int labelHeight, int fieldHeight) {
        int fieldTop = top + labelHeight + 3;
        return new Row(new UiRect(left, top, left + width, top + labelHeight),
                new UiRect(left, fieldTop, left + width, fieldTop + fieldHeight));
    }
}
