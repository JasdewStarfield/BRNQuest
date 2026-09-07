package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import java.util.ArrayList;
import java.util.List;

/** Builds the filtered, stable row projection used by publish-review rendering and input. */
public final class EditorPublishReviewRows {
    /** Blocking results precede recoverable warnings in both tabs and keyboard reading order. */
    public enum Filter { ALL, ERRORS, WARNINGS, CHANGES }
    public enum Kind { DIAGNOSTIC, CHANGE, EMPTY }
    public record Row(Kind kind, int sourceIndex) {}

    private EditorPublishReviewRows() {}

    public static List<Row> rows(EditorPublishReviewModel review, Filter filter) {
        if (review == null) return List.of();
        List<Row> result = new ArrayList<>();
        if (filter == Filter.ALL) {
            // The unfiltered review is still actionable: blocking errors lead, then warnings and information.
            appendDiagnostics(review, result, Filter.ERRORS);
            appendDiagnostics(review, result, Filter.WARNINGS);
            for (int i = 0; i < review.diagnostics().size(); i++) {
                String severity = review.diagnostics().get(i).severity();
                if (!matches(severity, Filter.ERRORS) && !matches(severity, Filter.WARNINGS)) {
                    result.add(new Row(Kind.DIAGNOSTIC, i));
                }
            }
        } else if (filter != Filter.CHANGES) {
            appendDiagnostics(review, result, filter);
        }
        if (filter == Filter.ALL || filter == Filter.CHANGES) {
            for (int i = 0; i < review.changes().size(); i++) result.add(new Row(Kind.CHANGE, i));
        }
        // An explicit empty row explains an empty filter instead of leaving an apparently broken panel.
        if (result.isEmpty() || filter == Filter.ALL && review.changes().isEmpty()) {
            result.add(new Row(Kind.EMPTY, -1));
        }
        return List.copyOf(result);
    }

    private static void appendDiagnostics(EditorPublishReviewModel review, List<Row> rows, Filter filter) {
        for (int i = 0; i < review.diagnostics().size(); i++) {
            if (matches(review.diagnostics().get(i).severity(), filter)) rows.add(new Row(Kind.DIAGNOSTIC, i));
        }
    }

    private static boolean matches(String severity, Filter filter) {
        if (filter == Filter.WARNINGS) return "WARN".equals(severity);
        return filter == Filter.ERRORS && ("ERROR".equals(severity) || "FATAL".equals(severity));
    }
}
