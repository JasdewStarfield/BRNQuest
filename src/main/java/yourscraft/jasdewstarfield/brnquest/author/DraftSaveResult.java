package yourscraft.jasdewstarfield.brnquest.author;

import java.nio.file.Path;
import java.util.List;
import yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic;

/** Successful deterministic draft save and its recoverable previous-directory backup. */
public record DraftSaveResult(DraftSnapshot snapshot, String previousRevision, Path backup,
                              RevisionCheck revisionCheck, List<Diagnostic> diagnostics) {
    public DraftSaveResult {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    DraftSaveResult(DraftSnapshot snapshot, String previousRevision, Path backup) {
        this(snapshot, previousRevision, backup, null, List.of());
    }
}
