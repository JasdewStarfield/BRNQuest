package yourscraft.jasdewstarfield.brnquest.author;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AuthorValidationServiceTest {
    @Test void reportsFieldPathsAndInvalidCoordinates() {
        ResourceLocation bookId = id("book");
        ResourceLocation groupId = id("group");
        ResourceLocation chapterId = id("chapter");
        TaskDefinition task = new TaskDefinition(bookId, id("item_task"), id("item"),
                Map.of("item", "{id:\"minecraft:stone\"}", "count", "0"), false);
        QuestDefinition quest = new QuestDefinition(bookId, id("quest"), chapterId, "Quest", "", "Description", "",
                Double.NaN, 0, List.of(), List.of(task), List.of(), "");
        QuestBookDefinition book = new QuestBookDefinition(bookId, 1, "Book",
                List.of(new ChapterGroupDefinition(bookId, groupId, "Group", 0)),
                List.of(new ChapterDefinition(bookId, chapterId, groupId, "Chapter", "", 0, List.of(quest))), Map.of());

        var diagnostics = AuthorValidationService.incremental(book, List.of(quest.id(), task.id()));

        assertTrue(diagnostics.stream().anyMatch(value -> value.code().equals("BQA-103") && value.path().equals("position")));
        assertTrue(diagnostics.stream().anyMatch(value -> value.code().equals("BQA-T-MINIMUM")
                && value.path().equals("config.count")));
        assertTrue(AuthorValidationService.blocksCommit(diagnostics));
    }

    @Test void fullValidationChecksObjectsOutsideIncrementalAffectedSet() {
        QuestBookDefinition book = new QuestBookDefinition(id("book"), 1, "Book", List.of(), List.of(), Map.of());
        assertTrue(AuthorValidationService.incremental(book, List.of()).isEmpty());
        QuestBookDefinition blankTitle = new QuestBookDefinition(id("book"), 1, "", List.of(), List.of(), Map.of());
        assertTrue(AuthorValidationService.full(blankTitle).stream().anyMatch(value -> value.code().equals("BQA-101")));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("brnquest", path);
    }
}
