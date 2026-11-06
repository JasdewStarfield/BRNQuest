package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPublishReviewModel;

import java.util.Locale;
import java.util.function.Predicate;

/** Maps stable diagnostic protocol values to localized, player-facing components. */
final class EditorDiagnosticPresentation {
    private static final String ERROR_PREFIX = "screen.brnquest.editor.error.";
    private static final String DIAGNOSTIC_PREFIX = "screen.brnquest.editor.diagnostic.";

    private EditorDiagnosticPresentation() {}

    static Component operationError(String code) {
        String safeCode = safeCode(code);
        String key = ERROR_PREFIX + safeCode;
        return I18n.exists(key) ? Component.translatable(key)
                : Component.translatable(ERROR_PREFIX + "UNKNOWN");
    }

    static Component diagnostic(EditorPublishReviewModel.Diagnostic diagnostic) {
        String key = translationKey(safeCode(diagnostic.code()), I18n::exists);
        if (!key.isBlank()) return Component.translatable(key);
        // Keep the server's actual reason visible when a code has no localized summary yet.
        if (diagnostic.message() != null && !diagnostic.message().isBlank())
            return Component.literal(diagnostic.message());
        String path = diagnostic.path() == null || diagnostic.path().isBlank()
                ? Component.translatable("screen.brnquest.editor.diagnostic.unknown_field").getString()
                : diagnostic.path();
        return Component.translatable(DIAGNOSTIC_PREFIX + "UNKNOWN", path);
    }

    /** Task/reward field validators share issue codes; explicit addon translations still take precedence. */
    static String translationKey(String code, Predicate<String> exists) {
        String key = DIAGNOSTIC_PREFIX + code;
        if (exists.test(key)) return key;
        if (code.startsWith("BQA-T-") || code.startsWith("BQA-R-")) {
            key = DIAGNOSTIC_PREFIX + "config." + code.substring("BQA-T-".length());
            if (exists.test(key)) return key;
        }
        // Revision conflicts also appear as operation errors, with the same recovery advice.
        key = ERROR_PREFIX + code;
        return exists.test(key) ? key : "";
    }

    static Component severity(String severity) {
        String normalized = severity == null ? "unknown" : severity.toLowerCase(Locale.ROOT);
        String key = "screen.brnquest.editor.diagnostic.severity." + normalized;
        return Component.translatable(I18n.exists(key) ? key
                : "screen.brnquest.editor.diagnostic.severity.unknown");
    }

    static String safeCode(String code) {
        return code == null || code.isBlank() ? "UNKNOWN" : code;
    }
}
