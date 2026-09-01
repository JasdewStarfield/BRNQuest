package yourscraft.jasdewstarfield.brnquest.compat.ftb;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.diagnostic.DiagnosticReport;

import java.util.List;

/** Pure importer output with semantic counts used by reports and acceptance tests. */
public record FtbImportResult(QuestBookDefinition book, DiagnosticReport report,
                              int chapterGroupCount, int chapterCount, int questCount,
                              int taskCount, int rewardCount, List<FtbFieldConversion> fieldConversions) {
    public FtbImportResult {
        fieldConversions = List.copyOf(fieldConversions);
    }

    /** Deterministic author-facing report containing both problems and successful field decisions. */
    public String reportJson() {
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        return gson.toJson(new SerializedReport(1, report.diagnostics(), fieldConversions));
    }

    private record SerializedReport(int schemaVersion, List<?> diagnostics,
                                    List<FtbFieldConversion> fieldConversions) {}
}
