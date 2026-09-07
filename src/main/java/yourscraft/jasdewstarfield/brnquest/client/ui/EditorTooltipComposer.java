package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPublishReviewModel;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPublishReviewText;

import java.util.ArrayList;
import java.util.List;

/** Composes localized primary text with raw protocol details reserved for technical tooltips. */
final class EditorTooltipComposer {
    private EditorTooltipComposer() {}

    static List<Component> operationError(String code, String rawMessage) {
        List<Component> lines = new ArrayList<>();
        lines.add(EditorDiagnosticPresentation.operationError(code));
        lines.add(Component.translatable("screen.brnquest.technical.code",
                EditorDiagnosticPresentation.safeCode(code)));
        if (rawMessage != null && !rawMessage.isBlank()) {
            lines.add(Component.translatable("screen.brnquest.technical.message", rawMessage));
        }
        return List.copyOf(lines);
    }

    static List<Component> diagnostic(EditorPublishReviewModel.Diagnostic diagnostic) {
        List<Component> lines = new ArrayList<>();
        lines.add(EditorDiagnosticPresentation.diagnostic(diagnostic));
        lines.add(Component.translatable("screen.brnquest.technical.code",
                EditorDiagnosticPresentation.safeCode(diagnostic.code())));
        if (diagnostic.objectId() != null && !diagnostic.objectId().isBlank()) {
            lines.add(Component.translatable("screen.brnquest.technical.object", diagnostic.objectId()));
        }
        if (diagnostic.message() != null && !diagnostic.message().isBlank()) {
            lines.add(Component.translatable("screen.brnquest.technical.message", diagnostic.message()));
        }
        return List.copyOf(lines);
    }

    static List<Component> change(EditorPublishReviewModel.Change change) {
        List<Component> lines = new ArrayList<>();
        lines.add(EditorPublishReviewText.heading(change));
        lines.add(EditorPublishReviewText.detail(change));
        lines.addAll(EditorPublishReviewText.valueTooltip(change));
        return List.copyOf(lines);
    }
}
