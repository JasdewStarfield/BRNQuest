package yourscraft.jasdewstarfield.brnquest.author;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DraftBookEditorTest {
    @Test void supportsStableIdCrudAcrossTheWholeBook() {
        QuestBookDefinition book = emptyBook();
        ChapterGroupDefinition group = new ChapterGroupDefinition(id("book"), id("group"), "Group", 0);
        book = value(DraftBookEditor.addGroup(book, group));
        ChapterDefinition firstChapter = new ChapterDefinition(id("book"), id("first"), group.id(), "First", "", 0, List.of());
        ChapterDefinition secondChapter = new ChapterDefinition(id("book"), id("second"), group.id(), "Second", "", 1, List.of());
        book = value(DraftBookEditor.addChapter(book, firstChapter));
        book = value(DraftBookEditor.addChapter(book, secondChapter));
        QuestDefinition root = quest("root", firstChapter.id(), List.of());
        QuestDefinition child = quest("child", firstChapter.id(), List.of());
        book = value(DraftBookEditor.addQuest(book, firstChapter.id(), root));
        book = value(DraftBookEditor.addQuest(book, firstChapter.id(), child));
        book = value(DraftBookEditor.addDependency(book, child.id(), root.id()));
        TaskDefinition task = new TaskDefinition(id("book"), id("task"), id("checkmark"), Map.of("title", "Done"), false);
        RewardDefinition reward = new RewardDefinition(id("book"), id("reward"), id("custom"), Map.of(), "manual", false);
        book = value(DraftBookEditor.addTask(book, child.id(), task));
        book = value(DraftBookEditor.addReward(book, child.id(), reward));
        book = value(DraftBookEditor.moveQuest(book, child.id(), secondChapter.id(), 0));

        QuestDefinition moved = book.quests().stream().filter(value -> value.id().equals(child.id())).findFirst().orElseThrow();
        assertEquals(secondChapter.id(), moved.chapterId());
        assertEquals(List.of(root.id()), moved.dependencies());
        assertEquals(task.id(), moved.tasks().getFirst().id());
        assertEquals(reward.id(), moved.rewards().getFirst().id());
        var diagnostics = AuthorValidationService.full(book);
        assertFalse(AuthorValidationService.blocksCommit(diagnostics), diagnostics.toString());
    }

    @Test void preventsImplicitCascadeAndContainerOverwrite() {
        QuestBookDefinition book = bookWithDependency();
        QuestDefinition child = book.quests().stream().filter(value -> value.id().equals(id("child"))).findFirst().orElseThrow();
        QuestDefinition maliciousReplacement = new QuestDefinition(id("book"), child.id(), child.chapterId(),
                "Updated", "", "", "", 2, 3, List.of(), List.of(), List.of(), "");

        QuestBookDefinition updated = value(DraftBookEditor.updateQuest(book, child.id(), maliciousReplacement));

        assertEquals(List.of(id("root")), updated.quests().stream()
                .filter(value -> value.id().equals(child.id())).findFirst().orElseThrow().dependencies());
        assertEquals("QUEST_IS_DEPENDENCY", DraftBookEditor.removeQuest(updated, id("root")).code());
        assertEquals("CHAPTER_NOT_EMPTY", DraftBookEditor.removeChapter(updated, id("chapter")).code());
        assertEquals("GROUP_NOT_EMPTY", DraftBookEditor.removeGroup(updated, id("group")).code());
    }

    @Test void rejectsDuplicateTypedIdsAndDependencyCycles() {
        QuestBookDefinition book = bookWithDependency();
        TaskDefinition first = new TaskDefinition(id("book"), id("typed"), id("checkmark"), Map.of(), false);
        book = value(DraftBookEditor.addTask(book, id("root"), first));
        RewardDefinition duplicate = new RewardDefinition(id("book"), first.id(), id("custom"), Map.of(), "manual", false);

        assertEquals("DUPLICATE_TYPED_ID", DraftBookEditor.addReward(book, id("root"), duplicate).code());
        QuestBookDefinition cycle = value(DraftBookEditor.addDependency(book, id("root"), id("child")));
        assertTrue(AuthorValidationService.full(cycle).stream().anyMatch(value -> value.code().equals("BQV-103")));
    }

    @Test void typedListsCopyReorderAndDeleteWithoutRewritingOpaqueConfig() {
        QuestBookDefinition book = bookWithDependency();
        TaskDefinition unknown = new TaskDefinition(id("book"), id("unknown_task"),
                ResourceLocation.parse("missing_extension:counter"), Map.of("opaque", "{value:7}"), true);
        TaskDefinition known = new TaskDefinition(id("book"), id("known_task"), id("checkmark"), Map.of(), false);
        RewardDefinition reward = new RewardDefinition(id("book"), id("reward"), id("custom"),
                Map.of("token", "alpha"), "manual", true);
        book = value(DraftBookEditor.addTask(book, id("root"), unknown));
        book = value(DraftBookEditor.addTask(book, id("root"), known));
        book = value(DraftBookEditor.addReward(book, id("root"), reward));

        TaskDefinition copied = new TaskDefinition(id("book"), id("unknown_copy"), unknown.typeId(),
                unknown.config(), unknown.optional());
        book = value(DraftBookEditor.copyTask(book, id("root"), unknown.id(), copied));
        book = value(DraftBookEditor.moveTask(book, id("root"), copied.id(), 0));
        book = value(DraftBookEditor.removeTask(book, id("root"), known.id()));

        QuestDefinition quest = book.quests().stream().filter(value -> value.id().equals(id("root")))
                .findFirst().orElseThrow();
        assertEquals(List.of(copied.id(), unknown.id()), quest.tasks().stream().map(TaskDefinition::id).toList());
        assertEquals(unknown.typeId(), quest.tasks().getFirst().typeId());
        assertEquals(unknown.config(), quest.tasks().getFirst().config());
        assertTrue(quest.tasks().getFirst().optional());
        assertEquals(Map.of("token", "alpha"), quest.rewards().getFirst().config());
        assertTrue(quest.rewards().getFirst().teamReward());
    }

    @Test void typedEntryRenamesPreservePropertiesOrderAndMigrationAliases() {
        QuestBookDefinition book = bookWithDependency();
        TaskDefinition firstTask = new TaskDefinition(id("book"), id("first_task"), id("item"),
                Map.of("item", "{id:\"minecraft:stone\",count:1}", "count", "4"), true);
        TaskDefinition secondTask = new TaskDefinition(id("book"), id("second_task"), id("checkmark"),
                Map.of("title", "Finish"), false);
        RewardDefinition firstReward = new RewardDefinition(id("book"), id("first_reward"), id("item"),
                Map.of("item", "{id:\"minecraft:diamond\",count:1}", "count", "2"), "auto", true);
        RewardDefinition secondReward = new RewardDefinition(id("book"), id("second_reward"), id("custom"),
                Map.of("token", "beta"), "manual", false);
        book = value(DraftBookEditor.addTask(book, id("root"), firstTask));
        book = value(DraftBookEditor.addTask(book, id("root"), secondTask));
        book = value(DraftBookEditor.addReward(book, id("root"), firstReward));
        book = value(DraftBookEditor.addReward(book, id("root"), secondReward));

        TaskDefinition renamedTask = new TaskDefinition(id("book"), id("renamed_task"), firstTask.typeId(),
                firstTask.config(), firstTask.optional());
        RewardDefinition renamedReward = new RewardDefinition(id("book"), id("renamed_reward"), firstReward.typeId(),
                firstReward.config(), firstReward.claimPolicy(), firstReward.teamReward());
        book = value(DraftBookEditor.updateTask(book, id("root"), firstTask.id(), renamedTask));
        book = value(DraftBookEditor.updateReward(book, id("root"), firstReward.id(), renamedReward));

        QuestDefinition quest = book.quests().stream().filter(value -> value.id().equals(id("root")))
                .findFirst().orElseThrow();
        assertEquals(List.of(renamedTask.id(), secondTask.id()), quest.tasks().stream().map(TaskDefinition::id).toList());
        assertEquals(firstTask.config(), quest.tasks().getFirst().config());
        assertTrue(quest.tasks().getFirst().optional());
        assertEquals(List.of(renamedReward.id(), secondReward.id()), quest.rewards().stream().map(RewardDefinition::id).toList());
        assertEquals("auto", quest.rewards().getFirst().claimPolicy());
        assertTrue(quest.rewards().getFirst().teamReward());
        assertEquals(renamedTask.id(), book.legacyIds().get("@task:" + firstTask.id()));
        assertEquals(renamedReward.id(), book.legacyIds().get("@reward:" + firstReward.id()));
        assertFalse(AuthorValidationService.blocksCommit(AuthorValidationService.full(book)));
    }

    @Test void typedEntryRenameRejectsDuplicateAndRemovalPrunesItsAlias() {
        QuestBookDefinition book = bookWithDependency();
        TaskDefinition source = new TaskDefinition(id("book"), id("source"), id("checkmark"), Map.of(), false);
        RewardDefinition occupied = new RewardDefinition(id("book"), id("occupied"), id("custom"), Map.of(), "manual", false);
        book = value(DraftBookEditor.addTask(book, id("root"), source));
        book = value(DraftBookEditor.addReward(book, id("root"), occupied));

        TaskDefinition duplicate = new TaskDefinition(id("book"), occupied.id(), source.typeId(), source.config(), false);
        assertEquals("DUPLICATE_TYPED_ID", DraftBookEditor.updateTask(book, id("root"), source.id(), duplicate).code());

        TaskDefinition renamed = new TaskDefinition(id("book"), id("renamed"), source.typeId(), source.config(), false);
        book = value(DraftBookEditor.updateTask(book, id("root"), source.id(), renamed));
        assertTrue(book.legacyIds().containsKey("@task:" + source.id()));
        book = value(DraftBookEditor.removeTask(book, id("root"), renamed.id()));
        assertFalse(book.legacyIds().containsKey("@task:" + source.id()));
    }

    @Test void explicitCascadeReportsAndRemovesItsImpactScope() {
        QuestBookDefinition book = bookWithDependency();

        AuthorOperationResult<DraftChange> result = DraftBookEditor.removeQuestAndReferences(book, id("root"));

        assertTrue(result.success());
        assertEquals(List.of(id("root"), id("child")), result.value().affectedObjects());
        QuestDefinition child = result.value().book().quests().getFirst();
        assertEquals(id("child"), child.id());
        assertTrue(child.dependencies().isEmpty());
        assertFalse(AuthorValidationService.blocksCommit(AuthorValidationService.full(result.value().book())));
    }

    @Test void reordersStructureAndCommitsMultipleNodePositionsAtomically() {
        QuestBookDefinition book = emptyBook();
        ChapterGroupDefinition firstGroup = new ChapterGroupDefinition(id("book"), id("first_group"), "First", 0);
        ChapterGroupDefinition secondGroup = new ChapterGroupDefinition(id("book"), id("second_group"), "Second", 1);
        book = value(DraftBookEditor.addGroup(book, firstGroup));
        book = value(DraftBookEditor.addGroup(book, secondGroup));
        ChapterDefinition first = new ChapterDefinition(id("book"), id("first"), firstGroup.id(), "First", "", 0, List.of());
        ChapterDefinition second = new ChapterDefinition(id("book"), id("second"), firstGroup.id(), "Second", "", 1, List.of());
        book = value(DraftBookEditor.addChapter(book, first));
        book = value(DraftBookEditor.addChapter(book, second));
        book = value(DraftBookEditor.addQuest(book, first.id(), quest("root", first.id(), List.of())));
        book = value(DraftBookEditor.addQuest(book, first.id(), quest("child", first.id(), List.of())));

        book = value(DraftBookEditor.moveGroup(book, secondGroup.id(), 0));
        book = value(DraftBookEditor.moveChapterOrder(book, second.id(), 0));
        book = value(DraftBookEditor.updateQuestPositions(book, Map.of(
                id("root"), new DraftBookEditor.Position(3.5, -2.0),
                id("child"), new DraftBookEditor.Position(5.0, 7.25))));

        assertEquals(0, book.chapterGroups().stream().filter(group -> group.id().equals(secondGroup.id()))
                .findFirst().orElseThrow().order());
        assertEquals(0, book.chapters().stream().filter(chapter -> chapter.id().equals(second.id()))
                .findFirst().orElseThrow().order());
        assertEquals(3.5, book.quests().stream().filter(quest -> quest.id().equals(id("root")))
                .findFirst().orElseThrow().x());
        assertEquals(7.25, book.quests().stream().filter(quest -> quest.id().equals(id("child")))
                .findFirst().orElseThrow().y());
    }

    @Test void explicitQuestRenamePreservesOrderAndUpdatesReferencesAndMigrationAliasAtomically() {
        QuestBookDefinition book = bookWithDependency();
        book = value(DraftBookEditor.addQuest(book, id("chapter"),
                quest("last", id("chapter"), List.of(id("child")))));
        QuestDefinition replacement = new QuestDefinition(id("book"), id("renamed"), id("chapter"),
                "Renamed", "Subtitle", "Updated", "minecraft:diamond", 99, 99,
                List.of(), List.of(), List.of(), "");

        QuestBookDefinition renamed = value(DraftBookEditor.updateQuestBasics(
                book, id("root"), replacement));

        assertEquals(List.of(id("renamed"), id("child"), id("last")),
                renamed.chapters().getFirst().quests().stream().map(QuestDefinition::id).toList());
        QuestDefinition updated = renamed.quests().stream()
                .filter(quest -> quest.id().equals(id("renamed"))).findFirst().orElseThrow();
        assertEquals("Renamed", updated.title());
        assertEquals("minecraft:diamond", updated.icon());
        assertEquals(0, updated.x(), "Property editing must not overwrite graph coordinates");
        assertEquals(List.of(id("renamed")), renamed.quests().stream()
                .filter(quest -> quest.id().equals(id("child"))).findFirst().orElseThrow().dependencies());
        assertEquals(id("renamed"), renamed.legacyIds().get("brnquest:root"));
    }

    @Test void questRenameRejectsAnExistingStableIdWithoutChangingTheBook() {
        QuestBookDefinition book = bookWithDependency();
        QuestDefinition duplicate = new QuestDefinition(id("book"), id("child"), id("chapter"),
                "Duplicate", "", "", "", 0, 0, List.of(), List.of(), List.of(), "");

        AuthorOperationResult<DraftChange> result = DraftBookEditor.updateQuestBasics(
                book, id("root"), duplicate);

        assertEquals("DUPLICATE_QUEST_ID", result.code());
        assertFalse(result.success());
    }

    private static QuestBookDefinition bookWithDependency() {
        QuestBookDefinition book = emptyBook();
        ChapterGroupDefinition group = new ChapterGroupDefinition(id("book"), id("group"), "Group", 0);
        book = value(DraftBookEditor.addGroup(book, group));
        ChapterDefinition chapter = new ChapterDefinition(id("book"), id("chapter"), group.id(), "Chapter", "", 0, List.of());
        book = value(DraftBookEditor.addChapter(book, chapter));
        book = value(DraftBookEditor.addQuest(book, chapter.id(), quest("root", chapter.id(), List.of())));
        book = value(DraftBookEditor.addQuest(book, chapter.id(), quest("child", chapter.id(), List.of(id("root")))));
        return book;
    }

    private static QuestBookDefinition emptyBook() {
        return new QuestBookDefinition(id("book"), 1, "Book", List.of(), List.of(), Map.of());
    }

    private static QuestDefinition quest(String path, ResourceLocation chapter, List<ResourceLocation> dependencies) {
        return new QuestDefinition(id("book"), id(path), chapter, path, "", "Description", "", 0, 0,
                dependencies, List.of(), List.of(), "");
    }

    private static QuestBookDefinition value(AuthorOperationResult<DraftChange> result) {
        assertTrue(result.success(), () -> result.code() + ": " + result.message());
        return result.value().book();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("brnquest", path);
    }
}
