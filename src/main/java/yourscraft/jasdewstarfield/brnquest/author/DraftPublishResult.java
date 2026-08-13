package yourscraft.jasdewstarfield.brnquest.author;

import yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic;

import java.nio.file.Path;
import java.util.List;

/** Result of publishing one validated, saved draft into the server author workspace. */
public record DraftPublishResult(DraftSnapshot snapshot, String previousWorkspaceRevision, Path backup,
                                 RevisionCheck revisionCheck, List<Diagnostic> diagnostics) {
    public DraftPublishResult {
        previousWorkspaceRevision = previousWorkspaceRevision == null ? "" : previousWorkspaceRevision;
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
