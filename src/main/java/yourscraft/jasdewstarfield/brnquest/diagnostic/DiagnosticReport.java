package yourscraft.jasdewstarfield.brnquest.diagnostic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import yourscraft.jasdewstarfield.brnquest.BrnQuestConstants;

import java.util.ArrayList;
import java.util.List;

/** Mutable collector with an immutable public result and deterministic JSON rendering. */
public final class DiagnosticReport {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final List<Diagnostic> diagnostics = new ArrayList<>();

    public void add(Diagnostic diagnostic) { diagnostics.add(diagnostic); }
    public List<Diagnostic> diagnostics() { return List.copyOf(diagnostics); }
    public boolean hasFatal() { return diagnostics.stream().anyMatch(d -> d.severity() == Diagnostic.Severity.FATAL); }
    public boolean hasErrors() { return diagnostics.stream().anyMatch(d -> d.severity().ordinal() >= Diagnostic.Severity.ERROR.ordinal()); }

    public String toJson() {
        return GSON.toJson(new SerializedReport(BrnQuestConstants.REPORT_SCHEMA, diagnostics()));
    }

    private record SerializedReport(int schemaVersion, List<Diagnostic> diagnostics) {}
}
