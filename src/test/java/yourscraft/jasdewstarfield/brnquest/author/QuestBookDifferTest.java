package yourscraft.jasdewstarfield.brnquest.author;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QuestBookDifferTest {
    @Test void reportsSemanticChangesInDeterministicOrder() {
        QuestBookDefinition before = book("old_id", "Old", 0, List.of(),
                new TaskDefinition(id("task"), id("task"), id("checkmark"), Map.of("title", "Before"), false));
        QuestBookDefinition after = book("new_id", "New", 4, List.of(id("dependency")),
                new TaskDefinition(id("task"), id("task"), id("custom"), Map.of("title", "After"), true));

        QuestBookDiff diff = QuestBookDiffer.diff(before, after);

        assertTrue(diff.entries().stream().anyMatch(entry -> entry.kind() == SemanticDiffEntry.Kind.RENAMED));
        assertTrue(diff.entries().stream().anyMatch(entry -> entry.kind() == SemanticDiffEntry.Kind.MOVED));
        assertTrue(diff.entries().stream().anyMatch(entry -> entry.kind() == SemanticDiffEntry.Kind.DEPENDENCY_ADDED));
        assertTrue(diff.entries().stream().anyMatch(entry -> entry.kind() == SemanticDiffEntry.Kind.TYPE_CHANGED));
        assertTrue(diff.entries().stream().anyMatch(entry -> entry.kind() == SemanticDiffEntry.Kind.CONFIG_CHANGED));
        assertEquals(diff.entries(), diff.entries().stream().sorted(java.util.Comparator
                .comparing((SemanticDiffEntry entry) -> entry.objectKind().ordinal())
                .thenComparing(entry -> entry.objectId().toString()).thenComparing(SemanticDiffEntry::path)
                .thenComparing(entry -> entry.kind().ordinal())).toList());
    }

    @Test void ignoresJsonFormattingAndFieldOrder() {
        QuestBookDefinition book = book("quest", "Quest", 0, List.of(),
                new TaskDefinition(id("task"), id("task"), id("checkmark"), Map.of("b", "2", "a", "1"), false));
        String encoded = NativeBookJson.encode(book);
        QuestBookDefinition decoded = NativeBookJson.decode(JsonParser.parseString("  " + encoded).getAsJsonObject());
        assertTrue(QuestBookDiffer.diff(book, decoded).empty());
    }

    @Test void reportsDescriptionFormatSeparatelyFromText() {
        QuestBookDefinition before = book("quest", "Quest", 0, List.of(),
                new TaskDefinition(id("task"), id("task"), id("checkmark"), Map.of(), false));
        QuestDefinition source = before.quests().getFirst();
        QuestDefinition markdown = new QuestDefinition(source.bookId(), source.id(), source.chapterId(),
                source.title(), source.subtitle(), source.description(),
                yourscraft.jasdewstarfield.brnquest.data.text.DocumentFormat.MARKDOWN_V1,
                source.icon(), source.x(), source.y(), source.dependencies(), source.tasks(), source.rewards(),
                source.legacyId(), source.appearance(), source.behavior(), source.extensions());
        ChapterDefinition chapter = before.chapters().getFirst();
        QuestBookDefinition after = new QuestBookDefinition(before.id(), before.schemaVersion(), before.title(),
                before.chapterGroups(), List.of(new ChapterDefinition(chapter.bookId(), chapter.id(), chapter.groupId(),
                chapter.title(), chapter.icon(), chapter.order(), List.of(markdown))), before.legacyIds());

        var changes = QuestBookDiffer.diff(before, after).entries();
        assertTrue(changes.stream().anyMatch(entry -> entry.path().equals("description_format")
                && entry.before().equals("plain") && entry.after().equals("markdown_v1")));
        assertFalse(changes.stream().anyMatch(entry -> entry.path().equals("description")));
    }

    @Test void reportsLocalizationAppearanceAndExtensionChanges() {
        QuestBookDefinition before = book("quest", "Quest", 0, List.of(),
                new TaskDefinition(id("task"), id("task"), id("checkmark"), Map.of(), false));
        QuestDefinition source = before.quests().getFirst();
        QuestDefinition changedQuest = new QuestDefinition(source.bookId(), source.id(), source.chapterId(),
                source.title(), source.subtitle(), source.description(), source.icon(), source.x(), source.y(),
                source.dependencies(), source.tasks(), source.rewards(), source.legacyId(),
                new QuestAppearance("circle", 2, 0.5, 1), Map.of("ftb.hide", "1b"));
        ChapterDefinition chapter = before.chapters().getFirst();
        QuestBookDefinition after = new QuestBookDefinition(before.id(), before.schemaVersion(), before.title(),
                before.chapterGroups(), List.of(new ChapterDefinition(chapter.bookId(), chapter.id(), chapter.groupId(),
                chapter.title(), chapter.icon(), chapter.order(), List.of(changedQuest), Map.of())), before.legacyIds(),
                new BookLocalization("en_us", Map.of("zh_cn", Map.of("quest.LEGACY.title", "任务"))), Map.of());

        QuestBookDiff diff = QuestBookDiffer.diff(before, after);
        assertTrue(diff.entries().stream().anyMatch(entry -> entry.path().equals("appearance.shape")));
        assertTrue(diff.entries().stream().anyMatch(entry -> entry.path().equals("extensions.ftb.hide")));
        assertTrue(diff.entries().stream().anyMatch(entry -> entry.path().contains("localization.zh_cn")));
    }

    private static QuestBookDefinition book(String questPath, String title, double x,
                                             List<ResourceLocation> dependencies, TaskDefinition task) {
        ResourceLocation bookId = id("book");
        ResourceLocation groupId = id("group");
        ResourceLocation chapterId = id("chapter");
        // The shared legacy ID lets the differ recognize an intentional quest rename.
        QuestDefinition quest = new QuestDefinition(bookId, id(questPath), chapterId, title, "", "", "",
                x, 0, dependencies, List.of(new TaskDefinition(bookId, task.id(), task.typeId(), task.config(), task.optional())),
                List.of(), "LEGACY");
        return new QuestBookDefinition(bookId, 1, "Book",
                List.of(new ChapterGroupDefinition(bookId, groupId, "Group", 0)),
                List.of(new ChapterDefinition(bookId, chapterId, groupId, "Chapter", "", 0, List.of(quest))), Map.of());
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("test", path); }
    @Test void removedObjectsRetainStructuredNamesAndUnknownConfiguration() {
        var original = book("quest", "Readable quest", 0, List.of(),
                new TaskDefinition(id("task"), id("task"), id("custom"),
                        Map.of("title", "External task", "addon_unknown", "{opaque:true}"), false));
        var diff = QuestBookDiffer.diff(original, null);
        var quest = diff.entries().stream().filter(e -> e.objectKind() == SemanticDiffEntry.ObjectKind.QUEST).findFirst().orElseThrow();
        assertEquals("Readable quest", JsonParser.parseString(quest.before()).getAsJsonObject().get("title").getAsString());
        var task = diff.entries().stream().filter(e -> e.objectKind() == SemanticDiffEntry.ObjectKind.TASK).findFirst().orElseThrow();
        assertEquals("{opaque:true}", JsonParser.parseString(task.before()).getAsJsonObject().getAsJsonObject("config").get("addon_unknown").getAsString());
        var chapter = diff.entries().stream().filter(e -> e.objectKind() == SemanticDiffEntry.ObjectKind.CHAPTER).findFirst().orElseThrow();
        assertEquals("Chapter", JsonParser.parseString(chapter.before()).getAsJsonObject().get("title").getAsString());
    }

    @Test void behaviorChangesShowExplicitBeforeAndAfterPerField() {
        var before = book("quest", "Quest", 0, List.of(),
                new TaskDefinition(id("task"), id("task"), id("checkmark"), Map.of(), false));
        var json = JsonParser.parseString(NativeBookJson.encode(before)).getAsJsonObject();
        var behavior = new com.google.gson.JsonObject();
        behavior.addProperty("repeatable", true);
        json.getAsJsonArray("chapters").get(0).getAsJsonObject().getAsJsonArray("quests").get(0)
                .getAsJsonObject().add("behavior", behavior);
        var diff = QuestBookDiffer.diff(before, NativeBookJson.decode(json));
        var change = diff.entries().stream().filter(e -> e.path().equals("behavior.repeatable")).findFirst().orElseThrow();
        assertEquals("false", change.before());
        assertEquals("true", change.after());
        assertFalse(diff.entries().stream().anyMatch(e -> e.path().equals("behavior")));
    }

}
