package yourscraft.jasdewstarfield.brnquest.runtime;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.BrnQuestConstants;
import yourscraft.jasdewstarfield.brnquest.data.*;
import yourscraft.jasdewstarfield.brnquest.diagnostic.*;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class QuestBookHealthTest {
    @Test void initialStateDoesNotInventALoadOrActiveBook() {
        var manager = new QuestBookManager();
        var health = manager.health();
        assertTrue(health.active().isEmpty());
        assertEquals(QuestBookHealth.Outcome.NOT_ATTEMPTED, health.reload().outcome());
        assertEquals(health, manager.health());
        assertFalse(manager.install(null, new DiagnosticReport()));
        assertEquals("", manager.health().reload().retainedRevision());
        assertTrue(manager.health().active().isEmpty());
    }

    @Test void successfulLoadHasStableReadOnlySnapshot() {
        var manager = QuestBookManager.get();
        assertTrue(manager.install(book(1), new DiagnosticReport(), id("resource")));
        var before = manager.health();
        assertEquals(QuestBookHealth.Outcome.INSTALLED, before.reload().outcome());
        assertEquals(id("book").toString(), before.reload().candidateBookId());
        assertEquals(1, before.reload().candidateQuestCount());
        assertEquals(id("resource"), before.activeResource().orElseThrow());
        assertEquals(before, manager.health());
        assertSame(before.active().orElseThrow(), manager.active().orElseThrow());
    }

    @Test void oversizedCandidateIsDistinctFromTheRetainedBook() {
        var manager = QuestBookManager.get();
        assertTrue(manager.install(book(1), new DiagnosticReport()));
        var active = manager.active().orElseThrow();
        assertFalse(manager.install(book(BrnQuestConstants.MAX_QUESTS + 1), new DiagnosticReport()));
        var health = manager.health();
        assertSame(active, health.active().orElseThrow());
        assertEquals(QuestBookHealth.Outcome.REJECTED, health.reload().outcome());
        assertEquals(4097, health.reload().candidateQuestCount());
        assertEquals(active.revision(), health.reload().retainedRevision());
        assertTrue(health.reload().diagnostics().stream().anyMatch(d -> d.code().equals("BQV-124")));
        assertThrows(UnsupportedOperationException.class, () -> health.reload().diagnostics().clear());
    }

    @Test void decodeFailureSurvivesLiveEditDiagnosticsAndSupersededReload() {
        var manager = QuestBookManager.get();
        assertTrue(manager.install(book(1), new DiagnosticReport()));
        var report = new DiagnosticReport();
        report.add(new Diagnostic(Diagnostic.Severity.FATAL, "BQV-003", "broken.json", "", "", "invalid JSON"));
        QuestBookReloadTransaction.reject(report);
        var failure = manager.health().reload();
        report.add(new Diagnostic(Diagnostic.Severity.FATAL, "LATE", "", "", "", "late"));
        manager.installLiveValidated(QuestBookSnapshot.of(book(2)), new DiagnosticReport());
        QuestBookReloadTransaction.abort();
        assertSame(failure, manager.health().reload());
        assertEquals(1, failure.diagnosticCount());
        assertEquals(2, manager.health().active().orElseThrow().book().quests().size());
        assertTrue(manager.install(book(3), new DiagnosticReport()));
        assertEquals(QuestBookHealth.Outcome.INSTALLED, manager.health().reload().outcome());
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("health_test", path); }
    private static QuestBookDefinition book(int count) {
        var quests = IntStream.range(0, count).mapToObj(i -> new QuestDefinition(id("book"), id("q" + i), id("chapter"),
                "Quest", "", "", "", 0, 0, List.of(), List.of(), List.of(), "")).toList();
        return new QuestBookDefinition(id("book"), 1, "Health fixture", List.of(new ChapterGroupDefinition(id("book"), id("group"), "Group", 0)),
                List.of(new ChapterDefinition(id("book"), id("chapter"), id("group"), "Chapter", "", 0, quests)), Map.of());
    }
}
