package yourscraft.jasdewstarfield.brnquest.compat.ftb.text;

import java.util.Objects;

/** Source-positioned conversion issue emitted before the value reaches BRNQuest Markdown. */
public record FtbTextDiagnostic(Severity severity, String code, FtbTextSource source, String message) {
    public FtbTextDiagnostic {
        severity = Objects.requireNonNull(severity, "severity");
        code = Objects.requireNonNullElse(code, "BQF-TEXT-UNKNOWN");
        source = Objects.requireNonNull(source, "source");
        message = Objects.requireNonNullElse(message, "");
    }

    public enum Severity { INFO, WARN, ERROR }
}
