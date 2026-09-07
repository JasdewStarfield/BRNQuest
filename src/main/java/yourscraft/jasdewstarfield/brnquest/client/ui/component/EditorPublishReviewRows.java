package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import yourscraft.jasdewstarfield.brnquest.network.AuthoringNetwork;

import java.util.ArrayList;
import java.util.List;

/** Builds the filtered, stable row projection used by publish-review rendering and input. */
public final class EditorPublishReviewRows {
    public enum Filter { ALL, WARNINGS, ERRORS, CHANGES }
    public enum Kind { DIAGNOSTIC, CHANGE, EMPTY }
    public record Row(Kind kind, int sourceIndex) {}

    private EditorPublishReviewRows() {}

    public static List<Row> rows(AuthoringNetwork.PublishReviewWire review, Filter filter) {
        if (review == null) return List.of();
        List<Row> result = new ArrayList<>();
        if (filter != Filter.CHANGES) {
            for (int i = 0; i < review.diagnostics().size(); i++) {
                if (matches(review.diagnostics().get(i).severity(), filter)) {
                    result.add(new Row(Kind.DIAGNOSTIC, i));
                }
            }
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

    private static boolean matches(String severity, Filter filter) {
        if (filter == Filter.ALL) return true;
        if (filter == Filter.WARNINGS) return "WARN".equals(severity);
        return filter == Filter.ERRORS && ("ERROR".equals(severity) || "FATAL".equals(severity));
    }
}
