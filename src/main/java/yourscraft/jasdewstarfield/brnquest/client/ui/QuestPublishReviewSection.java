package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButton;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPublishReviewPanel;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPublishReviewModel;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPublishReviewRows;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPublishReviewText;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorSmoothScroll;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.QuestScreenLayout;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;

import java.util.List;
import java.util.Locale;

/** Owns the publish-review overlay's immutable payload, filtering, scrolling and pointer frame. */
final class QuestPublishReviewSection {
    enum Action { CANCEL, CONFIRM, JUMP_TO_OBJECT }
    record Intent(Action action, String reviewedRevision, ResourceLocation objectId) {
        static Intent cancel() { return new Intent(Action.CANCEL, "", null); }
        static Intent confirm(String revision) { return new Intent(Action.CONFIRM, revision, null); }
        static Intent jump(ResourceLocation objectId) { return new Intent(Action.JUMP_TO_OBJECT, "", objectId); }
    }
    record RenderResult(List<Component> tooltip) {
        RenderResult { tooltip = tooltip == null ? List.of() : List.copyOf(tooltip); }
    }
    record ClickResult(boolean consumed, Intent intent) {}

    private final EditorSmoothScroll scroll = new EditorSmoothScroll();
    private EditorPublishReviewModel review;
    private EditorPublishReviewRows.Filter filter = EditorPublishReviewRows.Filter.ALL;

    void open(EditorPublishReviewModel review) {
        this.review = review;
        filter = EditorPublishReviewRows.Filter.ALL;
        scroll.snap(0);
    }

    void close() {
        review = null;
        filter = EditorPublishReviewRows.Filter.ALL;
        scroll.snap(0);
    }

    boolean active() {
        return review != null;
    }

    void mouseScrolled(QuestScreenLayout screen, double amount, double step) {
        if (review == null) return;
        EditorPublishReviewPanel.Layout layout = EditorPublishReviewPanel.layout(screen);
        scroll.scrollWheel(amount, step, rows().size() * EditorPublishReviewPanel.ROW_HEIGHT,
                layout.list().height());
    }

    RenderResult render(GuiGraphics graphics, Font font, QuestScreenLayout screen,
                        double elapsedSeconds, double smoothSpeed, int mouseX, int mouseY) {
        if (review == null) return new RenderResult(List.of());
        EditorPublishReviewPanel.Layout layout = EditorPublishReviewPanel.layout(screen);
        UiRect panel = layout.panel();
        graphics.fill(0, 0, screen.width(), screen.height(), 0x88000000);
        graphics.fill(panel.left(), panel.top(), panel.right(), panel.bottom(), 0xFF202832);
        graphics.drawCenteredString(font, Component.translatable("screen.brnquest.editor.publish.review.title"),
                panel.centerX(), panel.top() + 9, 0xFFFFFFFF);
        int readinessColor = review.publishAllowed() ? 0xFF83D69A : 0xFFFF8B8B;
        graphics.drawString(font, Component.translatable(review.publishAllowed()
                        ? "screen.brnquest.editor.publish.review.ready"
                        : "screen.brnquest.editor.publish.review.blocked"),
                panel.left() + 12, panel.top() + 26, readinessColor, false);
        graphics.drawString(font, Component.translatable("screen.brnquest.editor.publish.review.revisions",
                        shortRevision(review.fromRevision()), shortRevision(review.targetRevision())),
                panel.left() + 12, panel.top() + 40, 0xFFB7C5D8, false);
        graphics.drawString(font, Component.translatable("screen.brnquest.editor.publish.review.counts",
                        review.diagnosticCount(), review.changeCount(), review.truncated()
                                ? Component.translatable("screen.brnquest.editor.publish.review.truncated").getString()
                                : ""),
                panel.left() + 12, panel.top() + 54, 0xFFB7C5D8, false);
        graphics.drawString(font, Component.translatable("screen.brnquest.editor.publish.review.backup"),
                panel.left() + 12, panel.top() + 68, 0xFFFFC06A, false);
        renderFilters(graphics, font, layout, mouseX, mouseY);

        List<EditorPublishReviewRows.Row> rows = rows();
        scroll.frameAndRender(graphics, layout.list().right() + 2, layout.list().top(), layout.list().bottom(),
                rows.size() * EditorPublishReviewPanel.ROW_HEIGHT, layout.list().height(),
                elapsedSeconds, smoothSpeed);
        graphics.enableScissor(layout.list().left(), layout.list().top(),
                layout.list().right(), layout.list().bottom());
        List<Component> tooltip = List.of();
        int firstIndex = scroll.firstIndex(EditorPublishReviewPanel.ROW_HEIGHT);
        int rowOffset = scroll.rowOffset(EditorPublishReviewPanel.ROW_HEIGHT);
        int renderedRows = EditorPublishReviewPanel.renderedRows(layout, rowOffset);
        for (int visibleIndex = 0; visibleIndex < renderedRows; visibleIndex++) {
            int rowIndex = firstIndex + visibleIndex;
            if (rowIndex >= rows.size()) break;
            List<Component> rowTooltip = renderRow(graphics, font, layout, rows.get(rowIndex), rowIndex,
                    layout.list().top() + rowOffset + visibleIndex * EditorPublishReviewPanel.ROW_HEIGHT,
                    mouseX, mouseY);
            if (!rowTooltip.isEmpty()) tooltip = rowTooltip;
        }
        graphics.disableScissor();
        renderButton(graphics, font, layout.cancel(), Component.translatable("gui.cancel"), true,
                EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        renderButton(graphics, font, layout.confirm(), Component.translatable(review.publishAllowed()
                        ? "screen.brnquest.editor.publish.confirm"
                        : "screen.brnquest.editor.publish.blocked"),
                review.publishAllowed(), EditorButton.Tone.DANGER, mouseX, mouseY);
        return new RenderResult(tooltip);
    }

    ClickResult click(QuestScreenLayout screen, double mouseX, double mouseY, int button) {
        if (review == null || button != 0) return new ClickResult(true, null);
        EditorPublishReviewPanel.Layout layout = EditorPublishReviewPanel.layout(screen);
        if (scroll.handleTrackClick(mouseX, mouseY, layout.list().right() + 2,
                layout.list().top(), layout.list().bottom(),
                rows().size() * EditorPublishReviewPanel.ROW_HEIGHT, layout.list().height())) {
            return new ClickResult(true, null);
        }
        if (layout.cancel().contains(mouseX, mouseY)) return new ClickResult(true, Intent.cancel());
        EditorPublishReviewRows.Filter[] filters = EditorPublishReviewRows.Filter.values();
        int filterIndex = EditorPublishReviewPanel.filterAt(layout, filters.length, mouseX, mouseY);
        if (filterIndex >= 0) {
            filter = filters[filterIndex];
            scroll.snap(0);
            return new ClickResult(true, null);
        }
        if (review.publishAllowed() && layout.confirm().contains(mouseX, mouseY)) {
            return new ClickResult(true, Intent.confirm(review.targetRevision()));
        }
        List<EditorPublishReviewRows.Row> rows = rows();
        int row = EditorPublishReviewPanel.rowAt(layout, scroll, rows.size(), mouseX, mouseY);
        ResourceLocation objectId = row < 0 ? null : objectId(rows.get(row));
        return new ClickResult(true, objectId == null ? null : Intent.jump(objectId));
    }

    private List<Component> renderRow(GuiGraphics graphics, Font font, EditorPublishReviewPanel.Layout layout,
                                      EditorPublishReviewRows.Row reviewRow, int rowIndex,
                                      int y, int mouseX, int mouseY) {
        UiRect row = new UiRect(layout.list().left(), y, layout.list().right(),
                y + EditorPublishReviewPanel.ROW_HEIGHT - 2);
        boolean hovered = row.contains(mouseX, mouseY);
        graphics.fill(row.left(), row.top(), row.right(), row.bottom(),
                hovered ? 0xFF354352 : (rowIndex % 2 == 0 ? 0xFF28313C : 0xFF242C36));
        String heading;
        String detail;
        List<Component> tooltip;
        int headingColor;
        if (reviewRow.kind() == EditorPublishReviewRows.Kind.DIAGNOSTIC) {
            EditorPublishReviewModel.Diagnostic diagnostic = review.diagnostics().get(reviewRow.sourceIndex());
            heading = EditorDiagnosticPresentation.severity(diagnostic.severity()).getString() + " · "
                    + EditorDiagnosticPresentation.diagnostic(diagnostic).getString();
            detail = diagnostic.objectId() + (diagnostic.path().isBlank() ? "" : " · " + diagnostic.path());
            tooltip = EditorTooltipComposer.diagnostic(diagnostic);
            // Conventional severity colors keep recoverable warnings yellow and blocking errors red.
            headingColor = "WARN".equals(diagnostic.severity()) ? 0xFFFFD35A
                    : "ERROR".equals(diagnostic.severity()) || "FATAL".equals(diagnostic.severity())
                    ? 0xFFFF6B6B : 0xFF8FC7FF;
        } else if (reviewRow.kind() == EditorPublishReviewRows.Kind.EMPTY) {
            boolean changes = filter == EditorPublishReviewRows.Filter.CHANGES
                    || filter == EditorPublishReviewRows.Filter.ALL;
            heading = Component.translatable(changes
                    ? "screen.brnquest.editor.publish.review.no_changes"
                    : "screen.brnquest.editor.publish.review.no_matching").getString();
            detail = Component.translatable(changes
                    ? "screen.brnquest.editor.publish.review.no_changes.detail"
                    : "screen.brnquest.editor.publish.review.no_matching.detail").getString();
            tooltip = List.of(Component.literal(heading), Component.literal(detail));
            headingColor = 0xFF9FB0C2;
        } else {
            EditorPublishReviewModel.Change change = review.changes().get(reviewRow.sourceIndex());
            heading = EditorPublishReviewText.heading(change).getString();
            detail = EditorPublishReviewText.detail(change).getString();
            tooltip = EditorTooltipComposer.change(change);
            headingColor = 0xFF83D69A;
        }
        graphics.fill(row.left(), row.top(), row.left() + 3, row.bottom(), headingColor);
        int textWidth = Math.max(20, row.width() - 12);
        graphics.drawString(font, font.plainSubstrByWidth(heading, textWidth), row.left() + 7,
                row.top() + 3, headingColor, false);
        graphics.drawString(font, font.plainSubstrByWidth(detail, textWidth), row.left() + 5,
                row.top() + 15, 0xFF9FB0C2, false);
        return hovered ? tooltip : List.of();
    }

    private void renderFilters(GuiGraphics graphics, Font font, EditorPublishReviewPanel.Layout layout,
                               int mouseX, int mouseY) {
        EditorPublishReviewRows.Filter[] filters = EditorPublishReviewRows.Filter.values();
        for (int i = 0; i < filters.length; i++) {
            EditorPublishReviewRows.Filter candidate = filters[i];
            UiRect bounds = EditorPublishReviewPanel.filterBounds(layout, i, filters.length);
            boolean selected = candidate == filter;
            boolean hovered = bounds.containsExclusive(mouseX, mouseY);
            int textColor = switch (candidate) {
                case WARNINGS -> 0xFFFFD35A;
                case ERRORS -> 0xFFFF6B6B;
                case CHANGES -> 0xFF83D69A;
                case ALL -> 0xFFFFFFFF;
            };
            graphics.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(),
                    selected ? 0xFF405064 : hovered ? 0xFF354352 : 0xFF28313C);
            Component label = Component.translatable("screen.brnquest.editor.publish.review.filter."
                    + candidate.name().toLowerCase(Locale.ROOT));
            graphics.drawCenteredString(font, label, bounds.centerX(), bounds.top() + 5, textColor);
        }
    }

    private static void renderButton(GuiGraphics graphics, Font font, UiRect bounds, Component label,
                                     boolean enabled, EditorButton.Tone tone, int mouseX, int mouseY) {
        EditorButton.renderInteractive(graphics, font, bounds, EditorButton.Definition.text(label, null),
                enabled, false, tone, mouseX, mouseY);
    }

    private List<EditorPublishReviewRows.Row> rows() {
        return EditorPublishReviewRows.rows(review, filter);
    }

    private ResourceLocation objectId(EditorPublishReviewRows.Row row) {
        if (row.kind() == EditorPublishReviewRows.Kind.EMPTY) return null;
        String raw = row.kind() == EditorPublishReviewRows.Kind.DIAGNOSTIC
                ? review.diagnostics().get(row.sourceIndex()).objectId()
                : review.changes().get(row.sourceIndex()).objectId();
        return ResourceLocation.tryParse(raw);
    }

    private static String shortRevision(String revision) {
        if (revision == null || revision.isBlank()) return "-";
        return revision.length() <= 12 ? revision : revision.substring(0, 12);
    }
}
