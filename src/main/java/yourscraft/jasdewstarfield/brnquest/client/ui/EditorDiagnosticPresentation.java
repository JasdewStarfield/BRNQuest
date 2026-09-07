package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPublishReviewModel;

import java.util.Locale;

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
        String key = DIAGNOSTIC_PREFIX + safeCode(diagnostic.code());
        if (I18n.exists(key)) return Component.translatable(key);
        String path = diagnostic.path() == null || diagnostic.path().isBlank()
                ? Component.translatable("screen.brnquest.editor.diagnostic.unknown_field").getString()
                : diagnostic.path();
        return Component.translatable(DIAGNOSTIC_PREFIX + "UNKNOWN", path);
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
