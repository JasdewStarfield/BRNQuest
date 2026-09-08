package yourscraft.jasdewstarfield.brnquest.runtime;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.BRNQuest;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic;
import yourscraft.jasdewstarfield.brnquest.diagnostic.DiagnosticReport;

/** Coordinates script types and a resource-loaded task book as one server-thread replacement. */
public final class QuestBookReloadTransaction {
    private QuestBookReloadTransaction() {}

    public static boolean install(QuestBookDefinition book, DiagnosticReport report, ResourceLocation resource) {
        DiagnosticReport working = report == null ? new DiagnosticReport() : report;
        String scriptFailure = ScriptExtensionRegistry.reloadFailure();
        if (!scriptFailure.isBlank()) {
            working.add(new Diagnostic(Diagnostic.Severity.FATAL, "BQV-006", "", "", "", scriptFailure));
            ScriptExtensionRegistry.rollbackRegistration();
            QuestBookManager.get().retainAfterReloadFailure(book, working);
            BRNQuest.LOGGER.error("[BRNQuest/KUBEJS] Rejected script/task-book reload; retained revision {} and task types {}",
                    QuestBookManager.get().active().map(value -> value.revision()).orElse("<none>"),
                    ScriptExtensionRegistry.snapshot().taskTypeIds());
            return false;
        }

        // Candidate script types exist only for this validation thread. Runtime readers keep
        // resolving the last committed registry until the task book is known to be valid.
        return ScriptExtensionRegistry.withCandidateLookup(() -> QuestBookManager.get().installTransactional(
                book, working, resource, ScriptExtensionRegistry::commitSealedRegistration,
                ScriptExtensionRegistry::rollbackRegistration));
    }

    public static void reject(DiagnosticReport report) {
        ScriptExtensionRegistry.rollbackRegistration();
        QuestBookManager.get().retainAfterReloadFailure(report);
    }

    /** Cancels a superseded resource attempt without replacing its newer diagnostic report. */
    public static void abort() {
        ScriptExtensionRegistry.rollbackRegistration();
    }
}
