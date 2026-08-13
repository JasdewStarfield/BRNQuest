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
}
