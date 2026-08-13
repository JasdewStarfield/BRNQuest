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
