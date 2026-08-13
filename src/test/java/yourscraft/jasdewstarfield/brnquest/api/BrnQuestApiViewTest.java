package yourscraft.jasdewstarfield.brnquest.api;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.ChapterDefinition;
import yourscraft.jasdewstarfield.brnquest.data.ChapterGroupDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.data.RewardDefinition;
import yourscraft.jasdewstarfield.brnquest.data.TaskDefinition;
import yourscraft.jasdewstarfield.brnquest.diagnostic.DiagnosticReport;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrnQuestApiViewTest {
    private static final String LEGACY_QUEST = "0123456789ABCDEF";

    @BeforeEach
    void installBook() {
        ResourceLocation bookId = id("book");
        ResourceLocation groupId = id("group");
        ResourceLocation chapterId = id("chapter");
        TaskDefinition task = new TaskDefinition(bookId, id("task"), id("external_task"),
                Map.of("target", "3"), false);
        RewardDefinition reward = new RewardDefinition(bookId, id("reward"), id("external_reward"),
                Map.of("value", "token"), "manual", false);
        QuestDefinition quest = new QuestDefinition(bookId, id("quest"), chapterId, "Quest", "Subtitle",
                "Description", "minecraft:book", 2.5, -1.0, List.of(), List.of(task), List.of(reward),
                LEGACY_QUEST);
        QuestBookDefinition book = new QuestBookDefinition(bookId, 1, "Book",
                List.of(new ChapterGroupDefinition(bookId, groupId, "Group", 0)),
                List.of(new ChapterDefinition(bookId, chapterId, groupId, "Chapter", "minecraft:map", 0,
                        List.of(quest))), Map.of(LEGACY_QUEST, quest.id()));

        assertTrue(QuestBookManager.get().install(book, new DiagnosticReport()));
    }

    @Test
    void exposesTheCompleteDefinitionHierarchyInAuthorOrder() {
        QuestBookView book = BrnQuestApi.getActiveBook().orElseThrow();
        ChapterGroupView group = BrnQuestApi.getChapterGroups().getFirst();
        ChapterView chapter = BrnQuestApi.getChapters().getFirst();
        QuestView quest = BrnQuestApi.getQuest(LEGACY_QUEST).orElseThrow();

        assertEquals(List.of(id("group")), book.chapterGroupIds());
        assertEquals(List.of(id("chapter")), group.chapterIds());
        assertEquals(List.of(id("quest")), chapter.questIds());
        assertEquals("Subtitle", quest.subtitle());
        assertEquals(id("external_task"), quest.tasks().getFirst().typeId());
        assertEquals("token", quest.rewards().getFirst().config().get("value"));
        assertEquals(quest.tasks().getFirst(), BrnQuestApi.getTask("brnquest_test:task").orElseThrow());
        assertEquals(quest.rewards().getFirst(), BrnQuestApi.getReward("brnquest_test:reward").orElseThrow());
    }

    @Test
    void returnedCollectionsCannotMutateTheInstalledSnapshot() {
        QuestBookView book = BrnQuestApi.getActiveBook().orElseThrow();
        QuestView quest = BrnQuestApi.getQuest("brnquest_test:quest").orElseThrow();

        assertThrows(UnsupportedOperationException.class, () -> book.questIds().add(id("other")));
        assertThrows(UnsupportedOperationException.class, () -> book.legacyIds().clear());
        assertThrows(UnsupportedOperationException.class, () -> quest.tasks().getFirst().config().put("target", "9"));
        assertEquals("3", BrnQuestApi.getTask("brnquest_test:task").orElseThrow().config().get("target"));
    }

    @Test
    void malformedAndUnknownIdsAreNonThrowingQueries() {
        assertFalse(BrnQuestApi.getQuest("not a resource location").isPresent());
        assertFalse(BrnQuestApi.getChapter(null).isPresent());
        assertFalse(BrnQuestApi.getReward("brnquest_test:missing").isPresent());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("brnquest_test", path);
    }
}
