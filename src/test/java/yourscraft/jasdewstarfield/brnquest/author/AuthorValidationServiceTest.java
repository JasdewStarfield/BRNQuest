package yourscraft.jasdewstarfield.brnquest.author;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.BrnQuestConstants;
import yourscraft.jasdewstarfield.brnquest.data.ChapterDefinition;
import yourscraft.jasdewstarfield.brnquest.data.ChapterGroupDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.data.RewardDefinition;
import yourscraft.jasdewstarfield.brnquest.data.TaskDefinition;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypes;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorValidationServiceTest {
    @Test
    void unknownExtensionPlaceholderCanBeSavedAndPublished() {
        QuestBookDefinition baseline = book("Before");
        QuestBookDefinition candidate = book("After");
        var baselineDiagnostics = AuthorValidationService.full(baseline);
        var candidateDiagnostics = AuthorValidationService.incremental(candidate,
                List.of(ResourceLocation.parse("test:quest")));

        assertTrue(baselineDiagnostics.stream().anyMatch(value -> value.code().equals("BQV-117")
                && value.severity() == yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic.Severity.WARN));
        assertTrue(baselineDiagnostics.stream().anyMatch(value -> value.code().equals("BQV-118")
                && value.severity() == yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic.Severity.WARN));
        assertFalse(AuthorValidationService.blocksCommit(baselineDiagnostics),
                "A missing extension is a recoverable placeholder, not a publication failure");
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

    @Test
    void authoringRejectsTheFirstQuestBeyondTheSharedTransferLimit() {
        ResourceLocation bookId = ResourceLocation.parse("test:book");
        ResourceLocation groupId = ResourceLocation.parse("test:group");
        ResourceLocation chapterId = ResourceLocation.parse("test:chapter");
        List<QuestDefinition> quests = new ArrayList<>(BrnQuestConstants.MAX_QUESTS + 1);
        for (int index = 0; index <= BrnQuestConstants.MAX_QUESTS; index++) {
            quests.add(new QuestDefinition(bookId, ResourceLocation.fromNamespaceAndPath("test", "quest_" + index),
                    chapterId, "Quest " + index, "", "", "", index, 0,
                    List.of(), List.of(), List.of(), ""));
        }
        ChapterDefinition chapter = new ChapterDefinition(bookId, chapterId, groupId,
                "Chapter", "", 0, quests);
        QuestBookDefinition oversized = new QuestBookDefinition(bookId, 1, "Book",
                List.of(new ChapterGroupDefinition(bookId, groupId, "Group", 0)), List.of(chapter), Map.of());

        var diagnostics = AuthorValidationService.full(oversized);
        var diagnostic = diagnostics.stream()
                .filter(value -> value.code().equals("BQA-201") && value.path().equals("quests"))
                .findFirst().orElseThrow();
        assertTrue(diagnostics.stream()
                .anyMatch(value -> value.code().equals("BQV-124")
                        && value.severity() == yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic.Severity.FATAL));

        assertTrue(diagnostic.message().contains(Integer.toString(BrnQuestConstants.MAX_QUESTS)));
        assertTrue(AuthorValidationService.blocksCommit(List.of(diagnostic)));
    }

    private static QuestBookDefinition book(String questTitle) {
        ResourceLocation bookId = ResourceLocation.parse("test:book");
        ResourceLocation groupId = ResourceLocation.parse("test:group");
        ResourceLocation chapterId = ResourceLocation.parse("test:chapter");
        TaskDefinition unknown = new TaskDefinition(bookId, ResourceLocation.parse("test:unknown_task"),
                ResourceLocation.parse("missing:type"), Map.of("opaque", "preserved"), false);
        RewardDefinition unknownReward = new RewardDefinition(bookId, ResourceLocation.parse("test:unknown_reward"),
                ResourceLocation.parse("missing:reward_type"), Map.of("opaque", "preserved"), "manual", false);
        QuestDefinition quest = new QuestDefinition(bookId, ResourceLocation.parse("test:quest"), chapterId,
                questTitle, "", "", "", 0, 0, List.of(), List.of(unknown), List.of(unknownReward), "");
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
