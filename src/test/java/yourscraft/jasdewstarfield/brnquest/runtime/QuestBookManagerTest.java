package yourscraft.jasdewstarfield.brnquest.runtime;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic;
import yourscraft.jasdewstarfield.brnquest.diagnostic.DiagnosticReport;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QuestBookManagerTest {
    @Test void fatalReloadRetainsPreviousSnapshot() {
        QuestBookDefinition valid = new QuestBookDefinition(ResourceLocation.parse("test:main"), 1, "Valid", List.of(), List.of(), Map.of());
        assertTrue(QuestBookManager.get().install(valid, new DiagnosticReport()));
        String revision = QuestBookManager.get().active().orElseThrow().revision();
        DiagnosticReport fatal = new DiagnosticReport();
        fatal.add(new Diagnostic(Diagnostic.Severity.FATAL, "BQV-TEST", "", "", "", "broken"));
        assertFalse(QuestBookManager.get().install(null, fatal));
        assertEquals(revision, QuestBookManager.get().active().orElseThrow().revision());
    }

    @Test void installedDiagnosticReportIsAnImmutableSnapshot() {
        QuestBookDefinition valid = new QuestBookDefinition(ResourceLocation.parse("test:report"), 1, "Valid",
                List.of(), List.of(), Map.of());
        DiagnosticReport supplied = new DiagnosticReport();
        assertTrue(QuestBookManager.get().install(valid, supplied));
        supplied.add(new Diagnostic(Diagnostic.Severity.FATAL, "BQV-LATE", "", "", "", "late mutation"));

        assertFalse(QuestBookManager.get().lastReport().hasFatal());
        DiagnosticReport returned = QuestBookManager.get().lastReport();
        returned.add(new Diagnostic(Diagnostic.Severity.FATAL, "BQV-RETURN", "", "", "", "returned mutation"));
        assertFalse(QuestBookManager.get().lastReport().hasFatal());
    }

    @Test void nullBookIsAlwaysRejectedEvenWithoutCallerDiagnostic() {
        QuestBookDefinition valid = new QuestBookDefinition(ResourceLocation.parse("test:null_guard"), 1, "Valid",
                List.of(), List.of(), Map.of());
        assertTrue(QuestBookManager.get().install(valid, new DiagnosticReport()));
        String revision = QuestBookManager.get().active().orElseThrow().revision();

        assertFalse(QuestBookManager.get().install(null, new DiagnosticReport()));
        assertEquals(revision, QuestBookManager.get().active().orElseThrow().revision());
        assertTrue(QuestBookManager.get().lastReport().diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().equals("BQV-004")));
    }
}
