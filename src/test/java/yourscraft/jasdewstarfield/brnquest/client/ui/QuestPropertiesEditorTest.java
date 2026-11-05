package yourscraft.jasdewstarfield.brnquest.client.ui;

import com.google.gson.Gson;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.client.ClientEditorState;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorTextField;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
import yourscraft.jasdewstarfield.brnquest.data.*;
import yourscraft.jasdewstarfield.brnquest.network.AuthoringNetwork;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class QuestPropertiesEditorTest {
    @Test void cancelKeepsDraftAndApplyEmitsOnlyOneImmutableIntent() {
        var bookId = ResourceLocation.parse("test:properties");
        var chapterId = ResourceLocation.parse("test:chapter");
        var groupId = ResourceLocation.parse("test:group");
        var quest = new QuestDefinition(bookId, ResourceLocation.parse("test:quest"), chapterId,
                "Quest", "", "", "", 1, 2, List.of(), List.of(), List.of(), "");
        var snapshot = QuestBookSnapshot.of(new QuestBookDefinition(bookId, 1, "Book",
                List.of(new ChapterGroupDefinition(bookId, groupId, "Group", 0)),
                List.of(new ChapterDefinition(bookId, chapterId, groupId, "Chapter", "", 0, List.of(quest))), Map.of()));
        var editor = ClientEditorState.get();
        editor.disconnected();
        var gson = new Gson();
        editor.acceptCatalog(gson.toJson(new AuthoringNetwork.CatalogResponseWire("SUCCESS", "OK", "", true, List.of())));
        assertTrue(editor.beginOpenCurrent(bookId));
        var session = UUID.randomUUID();
        String json = NativeBookJson.encode(snapshot.book());
        editor.acceptSession(gson.toJson(new AuthoringNetwork.SessionResponseWire("OPEN", "SUCCESS", "OK", "",
                session.toString(), bookId.toString(), "", snapshot.revision(), snapshot.revision(), 36000, 1,
                json.getBytes(StandardCharsets.UTF_8).length)));
        assertTrue(editor.acceptDraftChunk(session.toString(), snapshot.revision(), 0, json));
        var submissions = new ArrayList<QuestPropertiesEditor.Submission>();
        var host = (QuestPropertiesEditor.Host) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{QuestPropertiesEditor.Host.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "snapshot" -> snapshot;
                    case "submit" -> { submissions.add((QuestPropertiesEditor.Submission) args[0]); yield true; }
                    default -> null;
                });
        var form = new QuestPropertiesEditor(host);
        var fields = new ArrayList<EditorTextField>();
        Font font = new Font(id -> null, false) {
            // The form only needs deterministic glyph widths, without a window or texture atlas.
            @Override public net.minecraft.client.StringSplitter getSplitter() {
                return new net.minecraft.client.StringSplitter((cp, style) -> 1);
            }
        };
        try {
            form.bind(font, fields::add);
            form.open(quest);
            fields.stream().filter(field -> field.getValue().equals("1.0")).findFirst().orElseThrow().setValue("7");
            assertTrue(form.changed());
            assertTrue(form.keyPressed(256));
            assertTrue(submissions.isEmpty());
            assertEquals(snapshot.revision(), editor.draft().orElseThrow().revision());
            form.open(quest);
            var viewport = new UiRect(0, 0, 100, 100);
            var save = new UiRect(0, 100, 40, 120);
            var cancel = new UiRect(50, 100, 90, 120);
            assertTrue(form.click(5, 105, 0, viewport, save, cancel));
            form.click(5, 105, 0, viewport, save, cancel);
            assertEquals(1, submissions.size());
            assertEquals(quest.id(), submissions.getFirst().originalId());
            assertFalse(form.open());

            // An authority rejection must stop a still-visible form before another render or tick.
            form.open(quest);
            editor.acceptSession(gson.toJson(new AuthoringNetwork.SessionResponseWire("RENEW", "EXPIRED",
                    "SESSION_EXPIRED", "Lease expired", session.toString(), bookId.toString(), "",
                    snapshot.revision(), snapshot.revision(), 0, 0, 0)));
            form.click(5, 105, 0, viewport, save, cancel);
            assertEquals(1, submissions.size(), "an expired lease cannot accept more property input");
        } finally { editor.disconnected(); }
    }
}
