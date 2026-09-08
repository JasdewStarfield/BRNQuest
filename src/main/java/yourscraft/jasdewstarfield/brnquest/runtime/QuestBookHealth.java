package yourscraft.jasdewstarfield.brnquest.runtime;

import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot;
import yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic;
import java.util.List;
import java.util.Optional;

/** One coherent read of active state and the last completed resource-book load, without renewing a lease. */
public record QuestBookHealth(Optional<QuestBookSnapshot> active, Optional<ResourceLocation> activeResource,
                              ReloadResult reload) {
    public enum Outcome { NOT_ATTEMPTED, INSTALLED, REJECTED }

    /** Candidate data remains separate from the book still active after a rejected reload. */
    public record ReloadResult(Outcome outcome, String completedAt, String candidateBookId, int candidateQuestCount,
                               String retainedRevision, int diagnosticCount, List<Diagnostic> diagnostics) {
        public ReloadResult { diagnostics = List.copyOf(diagnostics); }
        static ReloadResult empty() {
            return new ReloadResult(Outcome.NOT_ATTEMPTED, "", "", -1, "", 0, List.of());
        }
    }
}
