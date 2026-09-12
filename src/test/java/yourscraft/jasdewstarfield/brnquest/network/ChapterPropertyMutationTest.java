package yourscraft.jasdewstarfield.brnquest.network;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.*;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Protects old rename requests and chapter-owned content when the sidebar edits an icon. */
class ChapterPropertyMutationTest {
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
}
