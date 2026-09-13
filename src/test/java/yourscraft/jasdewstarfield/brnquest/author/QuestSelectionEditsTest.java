package yourscraft.jasdewstarfield.brnquest.author;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Selection copies keep graph structure and text while allocating independent progress identities. */
class QuestSelectionEditsTest {
    private static ResourceLocation id(String s) { return ResourceLocation.parse("test:" + s); }
    private static QuestDefinition quest(String s, double x, double y, List<ResourceLocation> dependencies) {
        return new QuestDefinition(id("book"), id(s), id("chapter"), s, "sub", "body", "", x, y, dependencies,
                List.of(new TaskDefinition(id("book"), id(s + "/task"), id("opaque"), Map.of("private_ref", "test:a"), true)),
                List.of(new RewardDefinition(id("book"), id(s + "/reward"), id("opaque"), Map.of("table", "nested"), "auto_hidden", true)), "");
    }
    private static QuestBookDefinition book() {
        var a = quest("a", 1.5, -2, List.of());
        var b = quest("b", 6.5, 4, List.of(a.id(), id("outside")));
        var outside = quest("outside", 10, 10, List.of(a.id()));
        return new QuestBookDefinition(id("book"), 1, "Book", List.of(new ChapterGroupDefinition(id("book"), id("g"), "Group", 0)),
                List.of(new ChapterDefinition(id("book"), id("chapter"), id("g"), "Chapter", "", 0, List.of(b, a, outside), Map.of(), QuestCreationDefaults.EMPTY, null, a.id())),
                Map.of(), new BookLocalization("en_us", Map.of("zh_cn", Map.of("quest.test:a.title", "甲", "quest.test:b.title", "乙"))), Map.of());
    }
    @Test void copyPreservesRelativeLayoutRemapsForwardEdgesAndKeepsExternalEdges() {
        var book = book();
        var changed = QuestSelectionEdits.copy(book, Set.of(id("a"), id("b")), 1, 1);
        assertTrue(changed.success());
        var result = changed.value().book();
        var a = result.quests().stream().filter(q -> q.title().equals("a (Copy)")).findFirst().orElseThrow();
        var b = result.quests().stream().filter(q -> q.title().equals("b (Copy)")).findFirst().orElseThrow();
        assertEquals(List.of(a.id(), id("outside")), b.dependencies());
        assertEquals(5, b.x() - a.x()); assertEquals(6, b.y() - a.y()); assertEquals(2.5, a.x());
        assertEquals("甲（副本）", BookText.quest(result, a, "zh_cn", "title", ""));
        assertTrue(a.tasks().getFirst().optional()); assertTrue(a.rewards().getFirst().teamReward());
        assertEquals("auto_hidden", a.rewards().getFirst().claimPolicy());
        assertEquals("test:a", a.tasks().getFirst().config().get("private_ref"));
        assertNotEquals(id("a/task"), a.tasks().getFirst().id());
        assertEquals(id("a"), result.chapters().getFirst().autofocusQuestId());
        assertEquals(result, NativeBookJson.decode(com.google.gson.JsonParser.parseString(NativeBookJson.encode(result)).getAsJsonObject()));
        assertEquals(7, QuestSelectionEdits.copy(result, Set.of(id("a"), id("b")), 1, 1).value().book().quests().size());
    }
    @Test void deleteRepairsOutsideReferencesAndAutofocusAndInvalidSelectionIsAtomic() {
        var book = book();
        var result = DraftBookEditor.removeQuestSelection(book, Set.of(id("a"), id("b"))).value().book();
        assertEquals(1, result.quests().size()); assertTrue(result.quests().getFirst().dependencies().isEmpty());
        assertNull(result.chapters().getFirst().autofocusQuestId());
        assertFalse(QuestSelectionEdits.copy(book, Set.of(id("a"), id("missing")), 1, 1).success());
        assertFalse(DraftBookEditor.removeQuestSelection(book, Set.of(id("missing"))).success());
        assertEquals(3, book.quests().size());
    }
}
