package yourscraft.jasdewstarfield.brnquest.client.ui.component;

/** Shared labeled-row geometry for quest, task, reward, and descriptor-driven editor forms. */
public final class EditorPropertyFormLayout {
    public static final int FIELD_HEIGHT = 18;

    public record Row(UiRect label, UiRect field) {}

    private EditorPropertyFormLayout() {}

    public static Row row(int left, int top, int width, int labelWidth) {
        int safeLabelWidth = Math.max(0, Math.min(labelWidth, width));
        return new Row(new UiRect(left, top, left + safeLabelWidth, top + FIELD_HEIGHT),
                new UiRect(left + safeLabelWidth, top, left + width, top + FIELD_HEIGHT));
    }
}
