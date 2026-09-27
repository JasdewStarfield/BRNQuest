package yourscraft.jasdewstarfield.brnquest.compat.ftb;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Failed conversions must produce readable reports without creating a draft. */
class FtbImportReportTest {
    @TempDir Path instance;

    @Test void fatalImportWritesFullDiagnosticsAndReplacesThePreviousReport() throws Exception {
        Path source = Files.createDirectories(instance.resolve("source"));
        // An unsupported version reproduces a conversion that cannot be saved as a draft.
        Files.writeString(source.resolve("data.snbt"), "{version:12}");
        Files.writeString(source.resolve("chapter_groups.snbt"), "{}");
        Files.createDirectories(source.resolve("chapters"));
        var result = new FtbV13Importer().importBook(source, "test", "main");
        assertTrue(result.report().hasFatal());
        result.report().add(new Diagnostic(Diagnostic.Severity.ERROR, "TEST", "章节.snbt",
                "quests[0]", "quest-id", "中文诊断"));
        Path reports = instance.resolve("config/brnquest/reports");
        Path report = FtbImportService.writeReport(reports, "test", "main", result);
        assertEquals(reports.resolve("import-test-main.json"), report);
        var json = JsonParser.parseString(Files.readString(report)).getAsJsonObject();
        assertEquals(JsonParser.parseString(result.reportJson()), json);
        assertTrue(Files.readString(report).contains("中文诊断"));
        assertFalse(Files.exists(instance.resolve("config/brnquest/drafts")));

        // A later attempt must replace stale errors rather than append another JSON document.
        Files.writeString(source.resolve("data.snbt"), "{version:13}");
        var corrected = new FtbV13Importer().importBook(source, "test", "main");
        assertFalse(corrected.report().hasErrors(), corrected.reportJson());
        assertEquals(report, FtbImportService.writeReport(reports, "test", "main", corrected));
        assertEquals(JsonParser.parseString(corrected.reportJson()), JsonParser.parseString(Files.readString(report)));
    }
}
