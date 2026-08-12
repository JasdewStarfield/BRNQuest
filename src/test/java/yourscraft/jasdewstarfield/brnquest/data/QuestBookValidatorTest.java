package yourscraft.jasdewstarfield.brnquest.data;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.diagnostic.DiagnosticReport;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QuestBookValidatorTest {
    @Test
    void topologicalOrderPlacesDependenciesFirst() {
        QuestDefinition first = quest("first", List.of());
        QuestDefinition second = quest("second", List.of(first.id()));
        QuestBookDefinition book = book(first, second);
        DiagnosticReport report = new DiagnosticReport();

        QuestBookValidator.validate(book, report);

        assertFalse(report.hasFatal(), report.toJson());
        assertTrue(QuestBookSnapshot.of(book).topologicalOrder().indexOf(first.id())
                < QuestBookSnapshot.of(book).topologicalOrder().indexOf(second.id()));
    }

    @Test
    void dependencyCycleIsFatal() {
        QuestDefinition first = quest("first", List.of(id("second")));
        QuestDefinition second = quest("second", List.of(id("first")));
        DiagnosticReport report = new DiagnosticReport();

        QuestBookValidator.validate(book(first, second), report);

        assertTrue(report.hasFatal());
        assertTrue(report.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("BQV-103")));
    }

    private static QuestBookDefinition book(QuestDefinition... quests) {
        ResourceLocation bookId = id("book");
        ResourceLocation groupId = id("group");
        return new QuestBookDefinition(bookId, 1, "Book",
                List.of(new ChapterGroupDefinition(bookId, groupId, "Group", 0)),
                List.of(new ChapterDefinition(bookId, id("chapter"), groupId, "Chapter", "", 0, List.of(quests))), Map.of());
    }

    private static QuestDefinition quest(String path, List<ResourceLocation> dependencies) {
        return new QuestDefinition(id("book"), id(path), id("chapter"), path, "", "", "", 0, 0,
                dependencies, List.of(), List.of(), path);
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("test", path); }
}
