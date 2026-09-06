package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.network.AuthoringNetwork;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Keeps server-authored English diagnostics out of primary UI while retaining them in technical details. */
final class EditorMessageText {
    private static final String ERROR_PREFIX = "screen.brnquest.editor.error.";
    private static final String DIAGNOSTIC_PREFIX = "screen.brnquest.editor.diagnostic.";

    private EditorMessageText() {}

    static Component operationError(String code) {
        String safeCode = safeCode(code);
        String key = ERROR_PREFIX + safeCode;
        return I18n.exists(key) ? Component.translatable(key)
                : Component.translatable(ERROR_PREFIX + "UNKNOWN");
    }

    static Component diagnostic(AuthoringNetwork.EditorDiagnosticWire diagnostic) {
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

    static List<Component> errorTooltip(String code, String rawMessage) {
        List<Component> lines = new ArrayList<>();
        lines.add(operationError(code));
        lines.add(Component.translatable("screen.brnquest.technical.code", safeCode(code)));
        if (rawMessage != null && !rawMessage.isBlank()) {
            lines.add(Component.translatable("screen.brnquest.technical.message", rawMessage));
        }
        return List.copyOf(lines);
    }

    static List<Component> diagnosticTooltip(AuthoringNetwork.EditorDiagnosticWire diagnostic) {
        List<Component> lines = new ArrayList<>();
        lines.add(diagnostic(diagnostic));
        lines.add(Component.translatable("screen.brnquest.technical.code", safeCode(diagnostic.code())));
        if (diagnostic.objectId() != null && !diagnostic.objectId().isBlank()) {
            lines.add(Component.translatable("screen.brnquest.technical.object", diagnostic.objectId()));
        }
        if (diagnostic.message() != null && !diagnostic.message().isBlank()) {
            lines.add(Component.translatable("screen.brnquest.technical.message", diagnostic.message()));
        }
        return List.copyOf(lines);
    }

    private static String safeCode(String code) {
        return code == null || code.isBlank() ? "UNKNOWN" : code;
    }
}
