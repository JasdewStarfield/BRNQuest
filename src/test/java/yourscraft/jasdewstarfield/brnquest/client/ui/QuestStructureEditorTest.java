package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.client.ClientEditorState;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.*;
import yourscraft.jasdewstarfield.brnquest.data.*;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class QuestStructureEditorTest {
    @Test void creationCancelDoesNotMutateAndConfirmedFormEmitsItsOwnValues() {
        var book = QuestBookSnapshot.of(new QuestBookDefinition(ResourceLocation.parse("test:structure"),
                1, "Book", List.of(), List.of(), Map.of()));
        var session = new EditorSessionView(UUID.randomUUID(), ClientEditorState.Mode.EDITING, true, false, "", Optional.of(book));
        var layout = new QuestScreenLayout(800, 480, false, true);
        var changes = new ArrayList<EditorMutationIntent>();
        var overlays = new EditorOverlayHost();
        var host = (QuestStructureEditor.Host) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{QuestStructureEditor.Host.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "session" -> session;
                    case "snapshot" -> book;
                    case "layout" -> layout;
                    case "closeOverlay" -> { overlays.close(); yield null; }
                    case "mutate" -> { changes.add((EditorMutationIntent) args[0]); yield true; }
                    default -> null;
                });
        var editor = new QuestStructureEditor(host, overlays);
        var fields = new HashMap<String, EditorTextField>();
        Font font = new Font(id -> null, false) {
            // Headless form edits need glyph distances, without opening a client window.
            @Override public net.minecraft.client.StringSplitter getSplitter() { return new net.minecraft.client.StringSplitter((cp, style) -> 1); }
        };
        editor.bind(font, field -> fields.put(((TranslatableContents) field.getMessage().getContents()).getKey(), field));
        var panel = layout.centeredDialog(380, 260, 20, 126);
        editor.open(QuestStructureEditor.Kind.ADD_GROUP, null, null, 0, 0);
        fields.get("screen.brnquest.editor.structure.title").setValue("Discarded");
        editor.click(panel.left() + 20, panel.bottom() - 20, 0);
        assertFalse(editor.open());
        assertTrue(changes.isEmpty());
        assertTrue(book.book().chapterGroups().isEmpty());
        editor.open(QuestStructureEditor.Kind.ADD_GROUP, null, null, 0, 0);
        fields.get("screen.brnquest.editor.structure.title").setValue("Confirmed");
        editor.click(panel.right() - 20, panel.bottom() - 20, 0);
        assertEquals(1, changes.size());
        assertEquals("ADD_GROUP", changes.getFirst().action());
        assertEquals("Confirmed", changes.getFirst().text());
        assertFalse(editor.open());
    }
}
