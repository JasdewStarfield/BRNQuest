package yourscraft.jasdewstarfield.brnquest.compat.ftb;

import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.diagnostic.DiagnosticReport;

/** Pure importer output with semantic counts used by reports and acceptance tests. */
public record FtbImportResult(QuestBookDefinition book, DiagnosticReport report,
                              int chapterGroupCount, int chapterCount, int questCount,
                              int taskCount, int rewardCount) {}
