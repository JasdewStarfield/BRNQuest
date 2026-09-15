package yourscraft.jasdewstarfield.brnquest.author;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Frozen selection tests cover coordinate anchors, localized data and stale external dependencies. */
class QuestClipboardSnapshotTest {
    private static ResourceLocation id(String s) { return ResourceLocation.parse("test:" + s); }
    private static QuestDefinition quest(String s, double x, double y, List<ResourceLocation> deps) {
        return new QuestDefinition(id("book"), id(s), id("src"), s, "subtitle", "body",
                yourscraft.jasdewstarfield.brnquest.data.text.DocumentFormat.MARKDOWN_V1, "", x, y, deps,
                List.of(new TaskDefinition(id("book"), id(s + "/task"), id("opaque"), Map.of("custom", "test:a", "consume_items", "true"), true)),
                List.of(new RewardDefinition(id("book"), id(s + "/reward"), id("opaque"), Map.of("nested", "{opaque}"), "auto_hidden", true)), "");
    }
    private static QuestBookDefinition book() {
        return new QuestBookDefinition(id("book"), 1, "Book", List.of(new ChapterGroupDefinition(id("book"), id("g"), "G", 0)),
                List.of(new ChapterDefinition(id("book"), id("src"), id("g"), "Source", "", 0,
                                List.of(quest("a", -3, 5, List.of()), quest("b", 2, -1, List.of(id("a"), id("outside"))), quest("outside", 0, 0, List.of()))),
                        new ChapterDefinition(id("book"), id("dest"), id("g"), "Destination", "", 1, List.of(), Map.of(), new QuestCreationDefaults(Map.of("size", "4", "repeatable", "true")), false, null)),
                Map.of(), new BookLocalization("en_us", Map.of("zh_cn", Map.of("quest.test:a.title", "甲",
                "quest.test:a.description", "原文", "quest.test:a.quest_desc", "**原文**",
                "quest.test:a.quest_desc_format", "markdown_v1"))), Map.of());
    }
    @Test void frozenSourcesSurviveDeletionAndPastePreservesLayoutAndExplicitFields() {
        var original = book();
        var frozen = QuestClipboardSnapshot.decode(QuestClipboardSnapshot.capture(original, Set.of(id("a"), id("b"))).encode());
        var destination = DraftBookEditor.removeQuestSelection(original, Set.of(id("a"), id("b"))).value().book();
        var operation = frozen.paste(destination, id("dest"), 10, 20); assertTrue(operation.success(), operation.message());
        var result = operation.value().book(); var quests = result.chapters().get(1).quests();
        var a = quests.getFirst(); var b = quests.get(1);
        assertEquals(10, a.x()); assertEquals(26, a.y()); assertEquals(15, b.x()); assertEquals(20, b.y());
        assertEquals(Set.of(a.id(), id("outside")), Set.copyOf(b.dependencies()));
        assertEquals("甲（副本）", BookText.quest(result, a, "zh_cn", "title", ""));
        assertEquals("原文", BookText.quest(result, a, "zh_cn", "description", ""));
        assertEquals(yourscraft.jasdewstarfield.brnquest.data.text.DocumentFormat.MARKDOWN_V1, a.descriptionFormat());
        assertEquals("markdown_v1", result.localization().translations().get("zh_cn")
                .get(BookText.questPrefix(a) + "quest_desc_format"));
        assertEquals(1.0, a.appearance().size()); assertFalse(a.behavior().repeatable());
        assertEquals("true", a.tasks().getFirst().config().get("consume_items")); assertTrue(a.tasks().getFirst().optional());
        assertEquals("auto_hidden", a.rewards().getFirst().claimPolicy()); assertTrue(a.rewards().getFirst().teamReward());
        assertNotEquals(id("a/task"), a.tasks().getFirst().id()); assertNull(result.chapters().get(1).autofocusQuestId());
        var repeat = frozen.paste(result, id("dest"), 0, 0).value().book();
        assertEquals(4, repeat.chapters().get(1).quests().size());
        assertEquals("甲（副本 2）", BookText.quest(repeat, repeat.chapters().get(1).quests().get(2), "zh_cn", "title", ""));
    }
    @Test void staleExternalDependencyAndInvalidDestinationsFailWithoutPartialResults() {
        var original = book(); var snapshot = QuestClipboardSnapshot.capture(original, Set.of(id("a"), id("b")));
        var destination = DraftBookEditor.removeQuestSelection(original, Set.of(id("outside"))).value().book();
        assertEquals("CLIPBOARD_DEPENDENCY_MISSING", snapshot.paste(destination, id("dest"), 0, 0).code());
        assertFalse(snapshot.paste(original, id("missing"), 0, 0).success());
        assertFalse(snapshot.paste(original, id("dest"), Double.NaN, 0).success());
        assertThrows(IllegalArgumentException.class, () -> snapshot.requireDestination(id("another_book")));
        assertThrows(IllegalArgumentException.class, () -> QuestClipboardSnapshot.decode("x".repeat(65537)));
        assertEquals(0, destination.chapters().get(1).quests().size());
    }
}
