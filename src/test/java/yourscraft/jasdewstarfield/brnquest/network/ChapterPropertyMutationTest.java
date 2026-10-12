package yourscraft.jasdewstarfield.brnquest.network;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.*;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Protects old rename requests and chapter-owned content when the sidebar edits an icon. */
class ChapterPropertyMutationTest {
    @Test void progressOverrideIsIndependentFromOtherChapterEditsAndCanReturnToClientPreference() {
        var chapter = new ChapterDefinition(ResourceLocation.parse("test:book"), ResourceLocation.parse("test:chapter"),
                ResourceLocation.parse("test:group"), "Chapter", "", 0, List.of(), Map.of("addon:opaque", "keep"));
        var book = new QuestBookDefinition(chapter.bookId(), 1, "Book", List.of(), List.of(chapter), Map.of());
        var shown = AuthoringMutationHandler.chapterReplacement(book, chapter.id(), chapter.groupId(), "Chapter", 0,
                Map.of("chapter_progress", "show"));
        assertTrue(DisplayOverrides.progress(shown.extensions()).resolve(false));
        assertEquals("keep", shown.extensions().get("addon:opaque"));
        var updated = new QuestBookDefinition(book.id(), 1, book.title(), List.of(), List.of(shown), Map.of());
        assertEquals(shown.extensions(), AuthoringMutationHandler.chapterReplacement(updated, chapter.id(),
                chapter.groupId(), "Renamed", 0, Map.of()).extensions());
        assertEquals(chapter.extensions(), AuthoringMutationHandler.chapterReplacement(updated, chapter.id(),
                chapter.groupId(), "Chapter", 0, Map.of("chapter_progress", "inherit")).extensions());
        assertThrows(IllegalArgumentException.class, () -> AuthoringMutationHandler.chapterReplacement(book, chapter.id(),
                chapter.groupId(), "Chapter", 0, Map.of("chapter_progress", "invalid")));
    }
    @Test void replacingOrClearingIconPreservesChapterContentsAndExtensions() {
        var bookId = ResourceLocation.parse("test:book");
        var chapterId = ResourceLocation.parse("test:chapter");
        var groupId = ResourceLocation.parse("test:group");
        var quest = new QuestDefinition(bookId, ResourceLocation.parse("test:quest"), chapterId,
                "Quest", "", "", "", 0, 0, List.of(), List.of(), List.of(), "");
        var chapter = new ChapterDefinition(bookId, chapterId, groupId, "Old", "texture:test:old.png",
                3, List.of(quest), Map.of("addon:opaque", "keep"));
        var book = new QuestBookDefinition(bookId, 1, "Book", List.of(), List.of(chapter), Map.of());
        for (var config : List.of(Map.<String, String>of(), Map.of("icon", ""),
                Map.of("icon", "{id:\"minecraft:stone\",count:1}"))) {
            var updated = AuthoringMutationHandler.chapterReplacement(book, chapterId, groupId, "New", 3, config);
            assertEquals(config.getOrDefault("icon", chapter.icon()), updated.icon());
            assertEquals("New", updated.title());
            assertEquals(chapter.quests(), updated.quests());
            assertEquals(chapter.extensions(), updated.extensions());
            assertEquals(chapter.groupId(), updated.groupId());
            assertEquals(chapter.order(), updated.order());
        }
    }
    @Test void changingGroupKeepsStableChapterIdentityAndOwnedData() {
        var bookId = ResourceLocation.parse("test:book");
        var chapterId = ResourceLocation.parse("test:chapter");
        var oldGroup = ResourceLocation.parse("test:old");
        var newGroup = ResourceLocation.parse("test:new");
        var chapter = new ChapterDefinition(bookId, chapterId, oldGroup, "Chapter", "", 2,
                List.of(), Map.of("addon:opaque", "keep"));
        var book = new QuestBookDefinition(bookId, 1, "Book", List.of(
                new ChapterGroupDefinition(bookId, oldGroup, "Old", 0),
                new ChapterGroupDefinition(bookId, newGroup, "New", 1)), List.of(chapter), Map.of());
        var replacement = AuthoringMutationHandler.chapterReplacement(book, chapterId, newGroup, "Chapter", 0, Map.of());
        var result = yourscraft.jasdewstarfield.brnquest.author.DraftBookEditor.updateChapter(book, chapterId, replacement);
        assertTrue(result.success());
        assertEquals(chapterId, replacement.id());
        assertEquals(newGroup, replacement.groupId());
        assertEquals(chapter.extensions(), replacement.extensions());
        assertEquals(chapter.quests(), replacement.quests());
        assertEquals(oldGroup, chapter.groupId()); // The original snapshot remains immutable.
    }
}
