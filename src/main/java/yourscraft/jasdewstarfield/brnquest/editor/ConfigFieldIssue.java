package yourscraft.jasdewstarfield.brnquest.editor;

import yourscraft.jasdewstarfield.brnquest.api.ApiStability;
import yourscraft.jasdewstarfield.brnquest.api.ApiStatus;

import java.util.Objects;

/** Stable field-level diagnostic suitable for an editor properties panel. */
@ApiStatus(ApiStability.EXPERIMENTAL)
public record ConfigFieldIssue(String fieldKey, Severity severity, String code, String message) {
    public ConfigFieldIssue {
        fieldKey = Objects.requireNonNull(fieldKey, "fieldKey");
        severity = Objects.requireNonNull(severity, "severity");
        code = Objects.requireNonNull(code, "code");
        message = Objects.requireNonNull(message, "message");
    }

    public enum Severity { WARNING, ERROR }
}
