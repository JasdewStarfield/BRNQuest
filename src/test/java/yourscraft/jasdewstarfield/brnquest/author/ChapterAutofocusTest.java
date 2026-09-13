package yourscraft.jasdewstarfield.brnquest.author;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.api.ApiViews;
import yourscraft.jasdewstarfield.brnquest.data.*;
import yourscraft.jasdewstarfield.brnquest.diagnostic.DiagnosticReport;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Focus is an owned reference: ordinary edits preserve it, while relocation/removal repairs it. */
class ChapterAutofocusTest {
    private static ResourceLocation id(String path) { return ResourceLocation.parse("test:" + path); }
    private static QuestDefinition quest(String name) {
        return new QuestDefinition(id("book"), id(name), id("chapter"), name, "", "", "", 12, -8,
                List.of(), List.of(), List.of(), "", QuestAppearance.DEFAULT, QuestBehavior.DEFAULT, Map.of());
    }
    private static ChapterDefinition chapter(ResourceLocation focus) {
        return new ChapterDefinition(id("book"), id("chapter"), id("group"), "Chapter", "", 0,
                List.of(quest("q"), quest("other")), Map.of(), QuestCreationDefaults.EMPTY, null, focus);
    }
    private static QuestBookDefinition book(ResourceLocation focus) {
        return new QuestBookDefinition(id("book"), 1, "Book",
                List.of(new ChapterGroupDefinition(id("book"), id("group"), "Group", 0)),
                List.of(chapter(focus), new ChapterDefinition(id("book"), id("second"), id("group"), "Second", "", 1, List.of())), Map.of());
    }
    @Test void roundTripAndPublicProjectionKeepTargetAndOldBooksOmitIt() {
        var book = book(id("q"));
        assertEquals(book, NativeBookJson.decode(JsonParser.parseString(NativeBookJson.encode(book)).getAsJsonObject()));
        assertEquals(id("q"), ApiViews.chapter(book.chapters().getFirst()).autofocusQuestId());
        assertFalse(NativeBookJson.encode(book(null)).contains("autofocus_id"));
    }
    @Test void renameAndSameChapterReorderPreserveFocus() {
        var renamed = DraftBookEditor.updateQuestBasics(book(id("q")), id("q"), quest("renamed")).value().book();
        assertEquals(id("renamed"), renamed.chapters().getFirst().autofocusQuestId());
        var reordered = DraftBookEditor.moveQuest(renamed, id("renamed"), id("chapter"), 1).value().book();
        assertEquals(id("renamed"), reordered.chapters().getFirst().autofocusQuestId());
        assertEquals(id("renamed"), reordered.chapters().getFirst().quests().getLast().id());
    }
    @Test void removalAndRelocationClearTarget() {
        var source = book(id("q"));
        assertNull(DraftBookEditor.removeQuest(source, id("q")).value().book().chapters().getFirst().autofocusQuestId());
        assertNull(DraftBookEditor.removeQuestAndReferences(source, id("q")).value().book().chapters().getFirst().autofocusQuestId());
        var moved = DraftBookEditor.moveQuest(source, id("q"), id("second"), 0).value().book();
        assertTrue(moved.chapters().stream().allMatch(c -> c.autofocusQuestId() == null));
        assertEquals(id("q"), DraftBookEditor.removeQuest(source, id("other")).value().book().chapters().getFirst().autofocusQuestId());
    }
    @Test void invalidTargetsAreRejectedAndExplicitClearIsAccepted() {
        var source = book(id("q"));
        assertFalse(DraftBookEditor.updateChapter(source, id("chapter"), chapter(id("missing"))).success());
        assertFalse(DraftBookEditor.updateChapter(source, id("chapter"), chapter(id("second"))).success());
        assertNull(DraftBookEditor.updateChapter(source, id("chapter"), chapter(null)).value().book().chapters().getFirst().autofocusQuestId());
        var report = new DiagnosticReport();
        QuestBookValidator.validate(book(id("missing")), report);
        assertTrue(report.toJson().contains("BQV-125"));
    }
}
