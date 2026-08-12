package yourscraft.jasdewstarfield.brnquest.diagnostic;

import java.util.Objects;

/** A stable, machine-readable problem reported without silently dropping content. */
public record Diagnostic(Severity severity, String code, String file, String path,
                         String objectId, String message) {
    public Diagnostic {
        Objects.requireNonNull(severity);
        code = Objects.requireNonNullElse(code, "BQR-000");
        file = Objects.requireNonNullElse(file, "");
        path = Objects.requireNonNullElse(path, "");
        objectId = Objects.requireNonNullElse(objectId, "");
        message = Objects.requireNonNullElse(message, "");
    }

    public enum Severity { INFO, WARN, ERROR, FATAL }
}
