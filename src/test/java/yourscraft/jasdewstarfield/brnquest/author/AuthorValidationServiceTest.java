package yourscraft.jasdewstarfield.brnquest.author;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.ChapterDefinition;
import yourscraft.jasdewstarfield.brnquest.data.ChapterGroupDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.data.TaskDefinition;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypes;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorValidationServiceTest {
    @Test
    void unchangedUnknownExtensionDoesNotBlockAnUnrelatedQuestTextEdit() {
        QuestBookDefinition baseline = book("Before");
        QuestBookDefinition candidate = book("After");
        var baselineDiagnostics = AuthorValidationService.full(baseline);
        var candidateDiagnostics = AuthorValidationService.incremental(candidate,
                List.of(ResourceLocation.parse("test:quest")));

        assertTrue(AuthorValidationService.blocksCommit(baselineDiagnostics));
        assertFalse(AuthorValidationService.blocksCommit(candidateDiagnostics, baselineDiagnostics));
        assertFalse(AuthorValidationService.blocksCommit(AuthorValidationService.full(candidate),
                baselineDiagnostics), "Full publish validation must also preserve source diagnostics");

        QuestBookDefinition invalid = book("");
        assertTrue(AuthorValidationService.blocksCommit(
                AuthorValidationService.incremental(invalid, List.of(ResourceLocation.parse("test:quest"))),
                baselineDiagnostics));
    }

    @Test
    void newlyIntroducedDependencyCycleBlocksIncrementalMutation() {
        QuestBookDefinition baseline = dependencyBook(false);
        QuestBookDefinition cyclic = dependencyBook(true);

        var diagnostics = AuthorValidationService.incremental(cyclic,
                List.of(ResourceLocation.parse("test:root"), ResourceLocation.parse("test:child")));

        assertTrue(diagnostics.stream().anyMatch(value -> value.code().equals("BQV-103")));
        assertTrue(AuthorValidationService.blocksCommit(diagnostics, AuthorValidationService.full(baseline)));
    }

    @Test
    void rawConfigCandidateCannotBypassTheRegisteredTypeCodec() {
        QuestBookDefinition baseline = itemBook(Map.of("item", "opaque-runtime-value"));
        QuestBookDefinition invalid = itemBook(Map.of("count", "1"));

        var diagnostics = AuthorValidationService.incremental(invalid, List.of(ResourceLocation.parse("test:quest")));

        assertTrue(diagnostics.stream().anyMatch(value -> value.code().equals("BQV-119")));
        assertTrue(AuthorValidationService.blocksCommit(diagnostics, AuthorValidationService.full(baseline)));
    }

    @Test
    void craftingOnlyObjectiveRejectsSeveralAcceptedEntries() {
        String matcher = "{\"mode\":\"list\",\"items\":["
                + "\"{count:1,id:\\\"minecraft:stone\\\"}\","
                + "\"{count:1,id:\\\"minecraft:dirt\\\"}\"],\"required\":1}";
        QuestBookDefinition invalid = itemBook(Map.of("matcher", matcher, "required_entries", "1",
                "only_from_crafting", "true"));

        var diagnostics = AuthorValidationService.full(invalid);

        assertTrue(diagnostics.stream().anyMatch(value -> value.code().equals("BQA-106")));
        assertTrue(AuthorValidationService.blocksCommit(diagnostics));
    }

    private static QuestBookDefinition book(String questTitle) {
        ResourceLocation bookId = ResourceLocation.parse("test:book");
        ResourceLocation groupId = ResourceLocation.parse("test:group");
        ResourceLocation chapterId = ResourceLocation.parse("test:chapter");
        TaskDefinition unknown = new TaskDefinition(bookId, ResourceLocation.parse("test:unknown_task"),
                ResourceLocation.parse("missing:type"), Map.of("opaque", "preserved"), false);
        QuestDefinition quest = new QuestDefinition(bookId, ResourceLocation.parse("test:quest"), chapterId,
                questTitle, "", "", "", 0, 0, List.of(), List.of(unknown), List.of(), "");
        ChapterDefinition chapter = new ChapterDefinition(bookId, chapterId, groupId, "Chapter", "", 0,
                List.of(quest));
        return new QuestBookDefinition(bookId, 1, "Book",
                List.of(new ChapterGroupDefinition(bookId, groupId, "Group", 0)), List.of(chapter), Map.of());
    }

    private static QuestBookDefinition dependencyBook(boolean cycle) {
        ResourceLocation bookId = ResourceLocation.parse("test:book");
        ResourceLocation groupId = ResourceLocation.parse("test:group");
        ResourceLocation chapterId = ResourceLocation.parse("test:chapter");
        ResourceLocation rootId = ResourceLocation.parse("test:root");
        ResourceLocation childId = ResourceLocation.parse("test:child");
        QuestDefinition root = new QuestDefinition(bookId, rootId, chapterId, "Root", "", "", "",
                0, 0, cycle ? List.of(childId) : List.of(), List.of(), List.of(), "");
        QuestDefinition child = new QuestDefinition(bookId, childId, chapterId, "Child", "", "", "",
                1, 0, List.of(rootId), List.of(), List.of(), "");
        ChapterDefinition chapter = new ChapterDefinition(bookId, chapterId, groupId, "Chapter", "", 0,
                List.of(root, child));
        return new QuestBookDefinition(bookId, 1, "Book",
                List.of(new ChapterGroupDefinition(bookId, groupId, "Group", 0)), List.of(chapter), Map.of());
    }

    private static QuestBookDefinition itemBook(Map<String, String> config) {
        ResourceLocation bookId = ResourceLocation.parse("test:book");
        ResourceLocation groupId = ResourceLocation.parse("test:group");
        ResourceLocation chapterId = ResourceLocation.parse("test:chapter");
        TaskDefinition task = new TaskDefinition(bookId, ResourceLocation.parse("test:item_task"),
                TaskTypes.ITEM, config, false);
        QuestDefinition quest = new QuestDefinition(bookId, ResourceLocation.parse("test:quest"), chapterId,
                "Quest", "", "", "", 0, 0, List.of(), List.of(task), List.of(), "");
        ChapterDefinition chapter = new ChapterDefinition(bookId, chapterId, groupId, "Chapter", "", 0,
                List.of(quest));
        return new QuestBookDefinition(bookId, 1, "Book",
                List.of(new ChapterGroupDefinition(bookId, groupId, "Group", 0)), List.of(chapter), Map.of());
    }
}
