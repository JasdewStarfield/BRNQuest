package yourscraft.jasdewstarfield.brnquest.compat.kubejs;

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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrnQuestKubeJSBindingsTest {
    private static final String LEGACY_QUEST = "1234567890ABCDEF";

    @BeforeEach
    void installBook() {
        installBook("Book");
    }

    private void installBook(String title) {
        ResourceLocation bookId = id("book");
        ResourceLocation groupId = id("group");
        ResourceLocation chapterId = id("chapter");
        TaskDefinition task = new TaskDefinition(bookId, id("task"), id("script_task"),
                Map.of("target", "4"), false);
        RewardDefinition reward = new RewardDefinition(bookId, id("reward"), id("script_reward"),
                Map.of("count", "2"), "manual", false);
        QuestDefinition quest = new QuestDefinition(bookId, id("quest"), chapterId, "Quest", "Subtitle",
                "Description", "minecraft:book", 1.5, 2.0, List.of(), List.of(task), List.of(reward), LEGACY_QUEST);
        QuestBookDefinition book = new QuestBookDefinition(bookId, 1, title,
                List.of(new ChapterGroupDefinition(bookId, groupId, "Group", 0)),
                List.of(new ChapterDefinition(bookId, chapterId, groupId, "Chapter", "minecraft:map", 0,
                        List.of(quest))), Map.of(LEGACY_QUEST, quest.id()));
        assertTrue(QuestBookManager.get().install(book, new DiagnosticReport()));
    }

    @Test
    void projectsDefinitionsAsImmutableScriptData() {
        Map<String, Object> book = BrnQuestKubeJSBindings.INSTANCE.getActiveBook();
        Map<String, Object> quest = BrnQuestKubeJSBindings.INSTANCE.getQuest(LEGACY_QUEST);

        assertEquals("brnquest_kubejs_test:book", book.get("id"));
        assertEquals(List.of("brnquest_kubejs_test:quest"), book.get("questIds"));
        assertEquals("Quest", quest.get("title"));
        assertEquals("brnquest_kubejs_test:script_task", firstMap(quest, "tasks").get("typeId"));
        assertEquals("2", nestedMap(firstMap(quest, "rewards"), "config").get("count"));
        assertThrows(UnsupportedOperationException.class, () -> book.put("title", "changed"));
        assertThrows(UnsupportedOperationException.class,
                () -> nestedMap(firstMap(quest, "tasks"), "config").put("target", "9"));
    }

    @Test
    void malformedQueriesAndInvalidPlayersRemainNonThrowing() {
        assertNull(BrnQuestKubeJSBindings.INSTANCE.getQuest("not a resource location"));
        assertFalse(BrnQuestKubeJSBindings.INSTANCE.isQuestCompleted(null, LEGACY_QUEST));

        Map<String, Object> result = BrnQuestKubeJSBindings.INSTANCE.completeQuest(null, LEGACY_QUEST);
        assertEquals("INVALID_REQUEST", result.get("status"));
        assertEquals("INVALID_PLAYER", result.get("code"));
        assertEquals(false, result.get("success"));
        assertEquals(false, result.get("changed"));
    }

    @Test
    void everyWriteMethodReturnsTheSameStructuredFailureShape() {
        List<Map<String, Object>> results = List.of(
                BrnQuestKubeJSBindings.INSTANCE.completeQuest(null, LEGACY_QUEST),
                BrnQuestKubeJSBindings.INSTANCE.submitQuest(null, LEGACY_QUEST),
                BrnQuestKubeJSBindings.INSTANCE.submitQuest(null, LEGACY_QUEST, true),
                BrnQuestKubeJSBindings.INSTANCE.completeTask(null, LEGACY_QUEST, id("task").toString()),
                BrnQuestKubeJSBindings.INSTANCE.addTaskProgress(null, id("task").toString(), 1L),
                BrnQuestKubeJSBindings.INSTANCE.claimReward(null, id("reward").toString()),
                BrnQuestKubeJSBindings.INSTANCE.claimAllRewards(null, LEGACY_QUEST),
                BrnQuestKubeJSBindings.INSTANCE.toggleTracked(null, LEGACY_QUEST),
                BrnQuestKubeJSBindings.INSTANCE.openQuest(null),
                BrnQuestKubeJSBindings.INSTANCE.openQuest(null, LEGACY_QUEST));

        for (Map<String, Object> result : results) {
            assertEquals(List.of("status", "code", "message", "success", "changed"),
                    List.copyOf(result.keySet()));
            assertEquals("INVALID_PLAYER", result.get("code"));
            assertThrows(UnsupportedOperationException.class, () -> result.put("changed", true));
        }
    }

    @Test
    void queriesResolveTheCurrentSnapshotOnEveryCall() {
        Map<String, Object> before = BrnQuestKubeJSBindings.INSTANCE.getActiveBook();
        installBook("Reloaded Book");
        Map<String, Object> after = BrnQuestKubeJSBindings.INSTANCE.getActiveBook();

        assertEquals("Book", before.get("title"));
        assertEquals("Reloaded Book", after.get("title"));
        assertFalse(before == after, "script projections must not be cached across task-book revisions");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstMap(Map<String, Object> parent, String key) {
        return (Map<String, Object>) ((List<?>) parent.get(key)).getFirst();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nestedMap(Map<String, Object> parent, String key) {
        return (Map<String, Object>) parent.get(key);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("brnquest_kubejs_test", path);
    }
}
