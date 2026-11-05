package yourscraft.jasdewstarfield.brnquest.client.ui;

import yourscraft.jasdewstarfield.brnquest.client.ui.component.GraystonePalette;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.client.ClientEditorState;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButton;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButtonInput;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPropertyPanel;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorTextField;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.QuestScreenLayout;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
import yourscraft.jasdewstarfield.brnquest.config.BrnQuestClientConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;

/** Shared detail-form viewport: owns scroll, row geometry and clipped native field rendering. */
final class EditorPropertySurface {
    interface Host {
        QuestScreenLayout layout();
        double elapsed();
        int drawerOffset();
        int canvasRight();
        EditorButtonInput buttons();
        boolean changed();
        void button(GuiGraphics g, UiRect bounds, Component label, boolean enabled, EditorButton.Tone tone, int x, int y);
    }
    private final Host host;
    private Font font;
    EditorPropertySurface(Host host) { this.host = host; }
    void bind(Font font) { this.font = font; propertyNativeFields.clear(); }
    void register(EditorTextField field) { propertyNativeFields.add(field); }
    void reset() { propertyScroll.snap(0); }
    boolean trackClick(double x, double y, int button) {
        var viewport = viewport();
        return button == 0 && propertyScroll.handleTrackClick(x, y, viewport.right() + 2,
                viewport.top(), viewport.bottom(), propertyContentHeight, viewport.height());
    }
    boolean drag(double y, int button) { return propertyScroll.handleDrag(y, button); }
    boolean release(int button) { return propertyScroll.handleRelease(button); }
    void wheel(double x, double y, double amount) {
        var viewport = viewport();
        if (viewport.contains(x, y)) propertyScroll.scrollWheel(amount, scrollStep(), propertyContentHeight, viewport.height());
    }
    void renderFields(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var clip = viewport().translated(host.drawerOffset(), 0);
        graphics.enableScissor(Math.max(host.canvasRight(), clip.left()), clip.top(), clip.right(), clip.bottom());
        try {
            for (var field : propertyNativeFields) field.render(graphics,
                    clip.containsExclusive(mouseX, mouseY) ? mouseX : Integer.MIN_VALUE,
                    clip.containsExclusive(mouseX, mouseY) ? mouseY : Integer.MIN_VALUE, partialTick);
        } finally { graphics.disableScissor(); }
    }
    private final yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorSmoothScroll propertyScroll = new yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorSmoothScroll();
    private double propertyDrawnScroll;
    private int propertyContentHeight;
    private List<UiRect> propertyRowLayouts = List.of();
    private final List<EditorTextField> propertyNativeFields = new ArrayList<>();
    UiRect row(int index) {
        return propertyRowLayouts.get(index).translated(0, -(int)Math.round(propertyDrawnScroll));
    }

    UiRect visible(UiRect bounds) {
        var viewport = viewport();
        var visible = bounds.intersection(viewport);
        return visible.width() > 0 && visible.height() > 0 ? visible : null;
    }

    UiRect viewport() {
        return new UiRect(detailLeft() + 10, topToolbarHeight() + 20,
                detailLeft() + detailsWidth() - 14, Math.max(topToolbarHeight() + 20, cancel().top() - 18));
    }

    void render(GuiGraphics graphics, Component heading, int headingColor,
                                     List<EditorPropertyPanel.RowContent> rows, Component done,
                                     EditorButton.Tone tone, int mouseX, int mouseY) {
        int width = host.layout().width(), height = host.layout().height();
        var viewport = viewport();
        var form = new EditorPropertyPanel.Layout(new UiRect(detailLeft(), topToolbarHeight(), width,
                height - bottomToolbarHeight() - 4), viewport.left(), viewport.width(),
                topToolbarHeight() + 7, viewport.top(), 22);
        EditorPropertyPanel.render(graphics, font, form, heading, headingColor, List.of(),
                new EditorPropertyPanel.Footer(cancel(), save(), done,
                        !ClientEditorState.get().busy(), tone),
                (g, bounds, label, enabled, buttonTone) -> host.button(g, bounds, label, enabled, buttonTone, mouseX, mouseY));
        propertyRowLayouts = EditorPropertyPanel.rows(form, rows);
        propertyContentHeight = propertyRowLayouts.isEmpty() ? 0
                : propertyRowLayouts.getLast().bottom() - viewport.top() + 4;
        propertyDrawnScroll = propertyScroll.frameAndRender(graphics, viewport.right() + 2, viewport.top(), viewport.bottom(),
                propertyContentHeight, viewport.height(), host.elapsed(), scrollSmoothSpeed());
        graphics.enableScissor(viewport.left(), viewport.top(), viewport.right(), viewport.bottom());
        host.buttons().viewport(viewport.translated(host.drawerOffset(), 0), host.drawerOffset());
        try {
            for (int index = 0; index < rows.size(); index++) {
                UiRect row = row(index);
                // Keep edge rows; the scissor clips their hidden pixels instead of removing the whole control.
                if (row.bottom() <= viewport.top() || row.top() >= viewport.bottom()) continue;
                rows.get(index).render(graphics, row.left(), row.top(), row.width());
            }
        } finally { graphics.disableScissor(); }
        graphics.drawString(font, Component.translatable(host.changed() ? "screen.brnquest.editor.scope.form"
                        : "screen.brnquest.editor.scope.unchanged"), viewport.left(),
                cancel().top() - 12, GraystonePalette.SECONDARY, false);
    }

    UiRect cancel() {
        int width = host.layout().width(), height = host.layout().height();
        int bottom = height - bottomToolbarHeight() - 6;
        int center = detailLeft() + detailsWidth() / 2;
        return new UiRect(detailLeft() + 10, bottom - 20, center - 4, bottom);
    }

    UiRect save() {
        int width = host.layout().width(), height = host.layout().height();
        int bottom = height - bottomToolbarHeight() - 6;
        int center = detailLeft() + detailsWidth() / 2;
        return new UiRect(center + 4, bottom - 20, width - 10, bottom);
    }

    private int detailLeft() { return host.layout().detailLeft(); }
    private int detailsWidth() { return host.layout().detailsWidth(); }
    private int topToolbarHeight() { return host.layout().topToolbarHeight(); }
    private int bottomToolbarHeight() { return host.layout().bottomToolbarHeight(); }
    private static double scrollStep() { return BrnQuestClientConfig.VALUES.scrollStep.get(); }
    private static double scrollSmoothSpeed() { return BrnQuestClientConfig.VALUES.smoothSpeed.get(); }
}
