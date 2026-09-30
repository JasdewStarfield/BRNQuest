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

    @Test
    void missingLegacyAliasTargetIsReported() {
        QuestBookDefinition original = book(quest("first", List.of()));
        QuestBookDefinition book = new QuestBookDefinition(original.id(), original.schemaVersion(), original.title(),
                original.chapterGroups(), original.chapters(), Map.of("OLD", id("missing")));
        DiagnosticReport report = new DiagnosticReport();

        QuestBookValidator.validate(book, report);

        assertTrue(report.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("BQV-121")));
    }

    @Test
    void missingDependencyAlsoMakesQuestUnreachable() {
        DiagnosticReport report = new DiagnosticReport();

        QuestBookValidator.validate(book(quest("blocked", List.of(id("missing")))), report);

        assertTrue(report.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("BQV-102")));
        assertTrue(report.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("BQV-122")));
    }

    @Test
    void importedAliasesCanReferenceEveryExistingObjectKind() {
        // FTB uses one untyped legacy-ID table for groups, chapters, quests, tasks and rewards.
        QuestDefinition plain = quest("first", List.of());
        var task = new TaskDefinition(id("book"), id("task"), id("unknown_task"), Map.of(), false);
        var reward = new RewardDefinition(id("book"), id("reward"), id("unknown_reward"), Map.of(), "manual", false);
        var original = book(new QuestDefinition(plain.bookId(), plain.id(), plain.chapterId(), plain.title(),
                plain.subtitle(), plain.description(), plain.icon(), plain.x(), plain.y(), plain.dependencies(),
                List.of(task), List.of(reward), plain.legacyId()));
        var aliases = Map.of("GROUP", id("group"), "CHAPTER", id("chapter"), "QUEST", plain.id(),
                "TASK", task.id(), "REWARD", reward.id(), "@task:old", task.id(), "@reward:old", reward.id());
        var restored = NativeBookJson.decode(com.google.gson.JsonParser.parseString(NativeBookJson.encode(
                new QuestBookDefinition(original.id(), original.schemaVersion(), original.title(),
                        original.chapterGroups(), original.chapters(), aliases))).getAsJsonObject());
        var report = new DiagnosticReport();
        QuestBookValidator.validate(restored, report);
        assertFalse(report.hasErrors(), report.toJson());
    }

    @Test
    void typedAliasesMustMatchTheirTargetKind() {
        // An editor task/reward alias pointing to a quest is still an invalid mapping.
        var original = book(quest("first", List.of()));
        var candidate = new QuestBookDefinition(original.id(), original.schemaVersion(), original.title(),
                original.chapterGroups(), original.chapters(),
                Map.of("@task:old", id("first"), "@reward:old", id("first")));
        var report = new DiagnosticReport();
        QuestBookValidator.validate(candidate, report);
        assertEquals(2, report.diagnostics().stream().filter(diagnostic -> diagnostic.code().equals("BQV-121")).count());
    }

    @Test
    void malformedExplicitTextureIconIsRejected() {
        QuestDefinition valid = quest("textured", List.of());
        QuestDefinition malformed = new QuestDefinition(valid.bookId(), valid.id(), valid.chapterId(), valid.title(),
                valid.subtitle(), valid.description(), "texture:not a resource location", valid.x(), valid.y(),
                valid.dependencies(), valid.tasks(), valid.rewards(), valid.legacyId());
        DiagnosticReport report = new DiagnosticReport();

        QuestBookValidator.validate(book(malformed), report);

        assertTrue(report.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("BQV-123")));
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
