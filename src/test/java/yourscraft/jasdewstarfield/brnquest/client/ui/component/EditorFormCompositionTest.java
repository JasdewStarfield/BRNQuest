package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import net.minecraft.client.gui.Font;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Native EditBox lifecycle without a GPU; only glyph measurement is replaced for deterministic text input. */
class EditorFormCompositionTest {
    private Font measuringFont() {
        return new Font(id -> null, false) {
            @Override public String plainSubstrByWidth(String text, int width) {
                return text.substring(0, Math.min(text.length(), Math.max(0, width)));
            }
            @Override public String plainSubstrByWidth(String text, int width, boolean tail) {
                int count = Math.min(text.length(), Math.max(0, width));
                return tail ? text.substring(text.length() - count) : text.substring(0, count);
            }
        };
    }
    @Test void resizeAndChildReturnReregisterSameTextCursorAndSelection() {
        var fields = new EditorFormFields<String>().define("title", "Title", 100).define("id", "ID", 50);
        var registered = new ArrayList<EditorTextField>();
        fields.bind(measuringFont(), registered::add);
        var title = fields.field("title");
        title.setValue("An unsaved title");
        title.setCursorPosition(3);
        title.setHighlightPos(8);
        registered.clear();
        fields.bind(measuringFont(), registered::add);
        assertSame(title, registered.getFirst());
        assertEquals("An unsaved title", title.getValue());
        assertEquals(3, title.getCursorPosition());
        assertEquals("unsav", title.getHighlighted());
        assertFalse(title.visible);
        assertFalse(title.active);
    }
    @Test void explicitPickerReturnRefreshesValueAndDoesNotAffectSiblingField() {
        var fields = new EditorFormFields<String>().define("item", "Item", 100).define("title", "Title", 100);
        fields.bind(measuringFont(), field -> {});
        fields.field("title").setValue("Keep me");
        fields.field("item").setValue("minecraft:oak_log");
        fields.bind(measuringFont(), field -> {});
        assertEquals("minecraft:oak_log", fields.field("item").getValue());
        assertEquals("Keep me", fields.field("title").getValue());
    }
    @Test void invalidDefinitionsAndUnboundAccessFailClearly() {
        var fields = new EditorFormFields<String>().define("x", "X", 20);
        assertThrows(IllegalArgumentException.class, () -> fields.define("x", "Again", 20));
        assertThrows(IllegalStateException.class, () -> fields.field("x"));
    }
    @Test void independentSettingsFormCombinesRowsAndFooterWithoutQuestScreen() {
        var fields = new EditorFormFields<String>().define("name", "Name", 100).define("color", "Color", 20);
        fields.bind(measuringFont(), field -> {});
        var layout = new EditorPropertyPanel.Layout(new UiRect(10, 10, 300, 200), 22, 260, 17, 30, 22);
        List<EditorPropertyPanel.RowContent> rows = List.of(
                EditorPropertyPanel.text(measuringFont(), fields.field("name"), "Name", 68, null, true),
                EditorPropertyPanel.text(measuringFont(), fields.field("color"), "Color", 68, "Bad color", true));
        assertEquals(2, rows.size());
        assertEquals(new UiRect(22, 30, 282, 48), layout.row(0));
        assertEquals(new UiRect(22, 52, 282, 70), layout.row(1));
        fields.field("color").setValue("blue");
        assertEquals("blue", fields.field("color").getValue());
    }
}
