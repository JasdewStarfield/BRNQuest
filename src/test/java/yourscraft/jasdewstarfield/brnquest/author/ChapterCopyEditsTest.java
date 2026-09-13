package yourscraft.jasdewstarfield.brnquest.author;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Chapter cloning preserves authoring metadata while remapping owned identities. */
class ChapterCopyEditsTest {
    private static ResourceLocation id(String s) { return ResourceLocation.parse("test:" + s); }
    private static QuestDefinition quest(String s, List<ResourceLocation> deps) {
        return new QuestDefinition(id("book"), id(s), id("chapter"), s, "sub", "body", "", 3, -4, deps,
                List.of(new TaskDefinition(id("book"), id(s + "/task"), id("opaque"), Map.of("ref", "test:a"), true)),
                List.of(new RewardDefinition(id("book"), id(s + "/reward"), id("opaque"), Map.of(), "auto_hidden", true)), "");
    }
    private static QuestBookDefinition book(boolean empty) {
        var source = new ChapterDefinition(id("book"), id("chapter"), id("g"), "Chapter", "minecraft:book", 0,
                empty ? List.of() : List.of(quest("a", List.of()), quest("b", List.of(id("a"), id("external")))),
                Map.of("custom", "kept"), QuestCreationDefaults.EMPTY, false, empty ? null : id("b"), true);
        var next = new ChapterDefinition(id("book"), id("next"), id("g"), "Next", "", 1, List.of());
        return new QuestBookDefinition(id("book"), 1, "Book", List.of(new ChapterGroupDefinition(id("book"), id("g"), "Group", 0)),
                List.of(source, next), Map.of("old_chapter", source.id()),
                new BookLocalization("en_us", Map.of("zh_cn", Map.of("chapter.old_chapter.title", "章节", "chapter.old_chapter.extra", "保留", "quest.test:a.title", "甲"))), Map.of());
    }
    @Test void copiesGraphLocalesFocusAndChapterMetadata() {
        var original = book(false);
        var change = ChapterCopyEdits.copy(original, id("chapter")); assertTrue(change.success(), change.message());
        var result = change.value().book();
        var copy = result.chapters().stream().filter(c -> c.title().equals("Chapter (Copy)")).findFirst().orElseThrow();
        assertTrue(copy.defaultHideDependencyLines());
        assertEquals(1, copy.order()); assertEquals(id("g"), copy.groupId());
        assertEquals("minecraft:book", copy.icon()); assertEquals(false, copy.consumeItems());
        assertEquals(Map.of("custom", "kept"), copy.extensions());
        var a = copy.quests().getFirst(); var b = copy.quests().get(1);
        assertEquals(List.of(a.id(), id("external")), b.dependencies()); assertEquals(b.id(), copy.autofocusQuestId());
        assertEquals(3, a.x()); assertEquals(-4, a.y());
        assertNotEquals(id("a/task"), a.tasks().getFirst().id()); assertNotEquals(id("a/reward"), a.rewards().getFirst().id());
        assertEquals("test:a", a.tasks().getFirst().config().get("ref"));
        assertEquals("章节（副本）", BookText.structureTitle(result, "chapter", copy.id(), "zh_cn", ""));
        assertEquals("保留", result.localization().translations().get("zh_cn").get("chapter." + copy.id() + ".extra"));
        assertEquals("甲（副本）", BookText.quest(result, a, "zh_cn", "title", ""));
        assertEquals(Set.of("zh_cn"), result.localization().translations().keySet());
        assertEquals(2, original.chapters().size()); assertEquals(id("b"), original.chapters().getFirst().autofocusQuestId());
        // Native serialization normalizes chapter list order using the explicit order field.
        String encoded = NativeBookJson.encode(result);
        assertEquals(encoded, NativeBookJson.encode(NativeBookJson.decode(com.google.gson.JsonParser.parseString(encoded).getAsJsonObject())));
        var twice = ChapterCopyEdits.copy(result, id("chapter")).value().book();
        assertTrue(twice.chapters().stream().anyMatch(c -> c.title().equals("Chapter (Copy 2)")));
    }
    @Test void emptyChaptersAreCopyableAndMissingSourceFails() {
        var original = book(true);
        var result = ChapterCopyEdits.copy(original, id("chapter")); assertTrue(result.success());
        assertEquals(3, result.value().book().chapters().size()); assertTrue(result.value().book().quests().isEmpty());
        assertFalse(ChapterCopyEdits.copy(original, id("missing")).success());
    }
}
