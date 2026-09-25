package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import java.util.List;

/** Immutable client presentation model copied from one server-authoritative publish review. */
public record EditorPublishReviewModel(boolean publishAllowed, String fromRevision, String targetRevision,
                                       String activeBookId, String activeRevision,
                                       int diagnosticCount, int changeCount, boolean truncated,
                                       List<Diagnostic> diagnostics, List<Change> changes) {
    public EditorPublishReviewModel(boolean publishAllowed, String fromRevision, String targetRevision,
                                    int diagnosticCount, int changeCount, boolean truncated,
                                    List<Diagnostic> diagnostics, List<Change> changes) {
        this(publishAllowed, fromRevision, targetRevision, "", "", diagnosticCount, changeCount,
                truncated, diagnostics, changes);
    }
    public EditorPublishReviewModel {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        changes = changes == null ? List.of() : List.copyOf(changes);
    }

    public record Diagnostic(String severity, String code, String objectId, String path, String message) {}
    public record Change(String kind, String objectKind, String objectId,
                         String path, String before, String after) {}
}
