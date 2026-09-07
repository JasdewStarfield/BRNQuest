package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.network.AuthoringNetwork;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EditorPublishReviewRowsTest {
    @Test void filterPriorityPutsBlockingErrorsBeforeWarnings() {
        assertEquals(List.of(EditorPublishReviewRows.Filter.ALL, EditorPublishReviewRows.Filter.ERRORS,
                        EditorPublishReviewRows.Filter.WARNINGS, EditorPublishReviewRows.Filter.CHANGES),
                List.of(EditorPublishReviewRows.Filter.values()));
    }

    @Test void filtersWarningsErrorsAndOrdinaryChangesWithoutLosingSourceIndices() {
        var review = new AuthoringNetwork.PublishReviewWire(false, "WORKSPACE", "old", "new", "BACKUP",
                3, 1, false, List.of(
                diagnostic("WARN", "warn"), diagnostic("ERROR", "error"), diagnostic("FATAL", "fatal")),
                List.of(new AuthoringNetwork.SemanticDiffWire(
                        "RENAMED", "QUEST", "test:quest", "title", "Before", "After")));

        assertEquals(List.of(new EditorPublishReviewRows.Row(EditorPublishReviewRows.Kind.DIAGNOSTIC, 0)),
                EditorPublishReviewRows.rows(review, EditorPublishReviewRows.Filter.WARNINGS));
        assertEquals(List.of(
                        new EditorPublishReviewRows.Row(EditorPublishReviewRows.Kind.DIAGNOSTIC, 1),
                        new EditorPublishReviewRows.Row(EditorPublishReviewRows.Kind.DIAGNOSTIC, 2)),
                EditorPublishReviewRows.rows(review, EditorPublishReviewRows.Filter.ERRORS));
        assertEquals(List.of(new EditorPublishReviewRows.Row(EditorPublishReviewRows.Kind.CHANGE, 0)),
                EditorPublishReviewRows.rows(review, EditorPublishReviewRows.Filter.CHANGES));
        assertEquals(List.of(
                        new EditorPublishReviewRows.Row(EditorPublishReviewRows.Kind.DIAGNOSTIC, 1),
                        new EditorPublishReviewRows.Row(EditorPublishReviewRows.Kind.DIAGNOSTIC, 2),
                        new EditorPublishReviewRows.Row(EditorPublishReviewRows.Kind.DIAGNOSTIC, 0),
                        new EditorPublishReviewRows.Row(EditorPublishReviewRows.Kind.CHANGE, 0)),
                EditorPublishReviewRows.rows(review, EditorPublishReviewRows.Filter.ALL));
    }

    @Test void emptyFilterStillExplainsItsResult() {
        var review = new AuthoringNetwork.PublishReviewWire(true, "WORKSPACE", "old", "new", "BACKUP",
                0, 0, false, List.of(), List.of());

        assertEquals(EditorPublishReviewRows.Kind.EMPTY,
                EditorPublishReviewRows.rows(review, EditorPublishReviewRows.Filter.WARNINGS).getFirst().kind());
        assertEquals(EditorPublishReviewRows.Kind.EMPTY,
                EditorPublishReviewRows.rows(review, EditorPublishReviewRows.Filter.CHANGES).getFirst().kind());
    }

    private static AuthoringNetwork.EditorDiagnosticWire diagnostic(String severity, String id) {
        return new AuthoringNetwork.EditorDiagnosticWire(severity, "TEST", "test:" + id, "", id);
    }
}
