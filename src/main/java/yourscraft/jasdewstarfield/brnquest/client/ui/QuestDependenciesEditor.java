package yourscraft.jasdewstarfield.brnquest.client.ui;

import yourscraft.jasdewstarfield.brnquest.client.ui.component.NativeText;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorTextRenderer;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.GraystonePalette;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.client.ClientEditorState;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButton;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorActionGroup;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorListPanel;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorOverlayHost;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPickerList;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.QuestActionIcons;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.QuestScreenLayout;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.config.BrnQuestClientConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;

/** Owns dependency selection, candidate search, rendered identity and add/remove intents. */
final class QuestDependenciesEditor {
    interface Host {
        QuestBookSnapshot snapshot();
        QuestDefinition selected();
        QuestScreenLayout layout();
        boolean panelReady();
        int drawerOffset();
        int canvasRight();
        double elapsed();
        String locale();
        String title(QuestDefinition quest);
        String chapterTitle(QuestBookDefinition book, ResourceLocation chapter);
        void tooltip(List<Component> text);
        void button(GuiGraphics g, UiRect bounds, EditorButton.Definition definition, boolean enabled, EditorButton.Tone tone, int mouseX, int mouseY);
        void change(Submission intent);
        void context(ResourceLocation dependency, int x, int y);
        void closeOverlay();
    }
    record Submission(boolean remove, ResourceLocation quest, ResourceLocation dependency) {}
    private final Host host;
    private final EditorOverlayHost editorOverlays;
    private Font font;
    private static final int DEPENDENCY_ROW_HEIGHT = 32;
    QuestDependenciesEditor(Host host, EditorOverlayHost overlays) { this.host = host; editorOverlays = overlays; }
    void bind(Font font) {
        this.font = font;
        dependencyList.invalidate(); dependencyActions.clear(); dependencyListSnapshot = null; dependencyPicker.invalidate();
    }
    boolean open() { return dependencyEditorOpen; }
    ResourceLocation target() { return dependencyEditorQuestId; }
    void message(Component value) { dependencyEditorMessage = value; }
    void closePicker() { dependencyFilter = ""; dependencyPicker.reset(); editorOverlays.close(); }
    boolean drag(double y, int button) { return dependencyList.mouseDragged(y, button); }
    boolean release(int button) { return dependencyList.mouseReleased(button); }
    boolean dragPicker(double y, int button) { return dependencyPicker.mouseDragged(y, button); }
    boolean releasePicker(int button) { return dependencyPicker.mouseReleased(button); }
    void wheel(double x, double y, double amount) {
        if (dependencyListInputReady()) dependencyList.mouseScrolled(x, y, amount, scrollStep());
    }
    void pickerWheel(double x, double y, double amount) { dependencyPicker.mouseScrolled(x, y, amount, scrollStep()); }
    boolean pickerKey(int key, int modifiers) {
        if (dependencyPicker.navigate(key, (modifiers & 1) != 0)) return true;
        if (key == 257 || key == 335) {
            dependencyPicker.focusedRow().ifPresent(row -> clickPicker(row.visible().centerX(), row.visible().centerY(), 0));
        } else if (key == 259 && !dependencyFilter.isEmpty()) {
            dependencyFilter = dependencyFilter.substring(0, dependencyFilter.length() - 1); dependencyPicker.reset();
        }
        return true;
    }
    boolean pickerText(char character) {
        if (!Character.isISOControl(character) && dependencyFilter.length() < 48) {
            dependencyFilter += character; dependencyPicker.reset();
        }
        return true;
    }
    private boolean dependencyEditorOpen;
    private ResourceLocation dependencyEditorQuestId;
    private final EditorListPanel<ResourceLocation> dependencyList = new EditorListPanel<>();
    private final EditorActionGroup<ResourceLocation> dependencyActions = new EditorActionGroup<>();
    private QuestBookSnapshot dependencyListSnapshot;
    private String dependencyFilter = "";
    private final EditorPickerList<QuestDependencyEditorModel.Candidate> dependencyPicker = new EditorPickerList<>();
    private Component dependencyEditorMessage;
    void render(GuiGraphics graphics, QuestDefinition quest, int mouseX, int mouseY) {
        int offset = host.drawerOffset();
        graphics.pose().pushPose();
        graphics.pose().translate(-offset, 0, 0);
        try {
            int left = detailLeft() + 10 + offset, right = width() - 10 + offset;
            graphics.fill(detailLeft() + 4 + offset, topToolbarHeight() + 4, width() - 4 + offset,
                    height() - bottomToolbarHeight() - 4, GraystonePalette.PANEL);
            graphics.drawString(font, Component.translatable("screen.brnquest.editor.dependency.heading"),
                    left, topToolbarHeight() + 12, 0xFFFFFFFF, false);
            graphics.drawString(font, Component.translatable("screen.brnquest.editor.dependency.direction"),
                    left, topToolbarHeight() + 27, GraystonePalette.SECONDARY, false);
            QuestBookSnapshot snapshot = host.snapshot();
            dependencyListSnapshot = snapshot;
            UiRect viewport = dependencyListBounds().translated(offset, 0);
            UiRect clip = new UiRect(host.canvasRight(), topToolbarHeight(), width(), height() - bottomToolbarHeight());
            dependencyList.advance(viewport, clip, width() - 8 + offset, DEPENDENCY_ROW_HEIGHT, 2,
                    quest.dependencies().size(), quest.dependencies()::get, host.elapsed(), scrollSmoothSpeed());
            List<EditorActionGroup.Placed<ResourceLocation>> actions = new ArrayList<>();
            dependencyList.render(graphics, row -> {
                QuestDefinition dependency = snapshot == null ? null : snapshot.quests().get(row.key());
                UiRect rect = row.bounds();
                UiRect remove = new UiRect(rect.right() - 20, rect.top() + 5, rect.right() - 2, rect.top() + 23);
                graphics.fill(rect.left(), rect.top(), rect.right(), rect.bottom(), GraystonePalette.ROW);
                String title = dependency == null ? row.key().toString() : host.title(dependency);
                Component direction = Component.translatable("screen.brnquest.editor.dependency.edge", NativeText.component(title), NativeText.component(host.title(quest)));
                int textWidth = Math.max(0, rect.width() - 30);
                EditorTextRenderer.drawFittedString(graphics, font, direction, rect.left()+5, rect.top()+4, textWidth, dependency == null ? 0xFFFFA070 : 0xFFFFFFFF, .75F);
                Component secondary = dependency == null ? Component.translatable("screen.brnquest.editor.dependency.missing")
                        : Component.literal(host.chapterTitle(snapshot.book(), dependency.chapterId()));
                EditorTextRenderer.drawCaption(graphics, font, secondary,
                        new UiRect(rect.left()+5,rect.top()+16,rect.left()+5+textWidth,rect.top()+25), row.visible(),
                        dependency == null ? 0xFFFFA070 : GraystonePalette.SECONDARY, mouseX+offset, mouseY);
                Component label = Component.translatable("screen.brnquest.editor.dependency.remove");
                actions.add(new EditorActionGroup.Placed<>(new EditorActionGroup.Action<>(row.key(),
                        EditorButton.Definition.iconOnly(label, label, QuestActionIcons.named("close")),
                        !ClientEditorState.get().busy(), EditorButton.Tone.DANGER, (x, y) -> {
                            if (!dependencyListInputReady() || ClientEditorState.get().busy()) return;
                            if (!host.selected().dependencies().contains(row.key())) return;
                            host.change(new Submission(true, dependencyEditorQuestId, row.key()));
                            dependencyEditorMessage = null;
                        }), remove, row.visible()));
                if (row.visible().containsExclusive(mouseX, mouseY) && !row.clip(remove).containsExclusive(mouseX, mouseY)) {
                    host.tooltip(List.of(Component.translatable("screen.brnquest.editor.dependency.more_hint")));
                }
            }, () -> graphics.drawCenteredString(font, Component.translatable("screen.brnquest.editor.dependency.empty"),
                    (left + right) / 2, viewport.top() + 8, GraystonePalette.MUTED));
            dependencyActions.setActions(actions);
            List<Component> tooltip = dependencyActions.render(graphics, font, mouseX, mouseY);
            if (!tooltip.isEmpty()) host.tooltip(tooltip);
            if (dependencyEditorMessage != null) {
                EditorTextRenderer.drawCaption(graphics, font, dependencyEditorMessage,
                        new UiRect(left,dependencyAddBounds().top()-12,left+detailsWidth()-24,dependencyAddBounds().top()-3),
                        0xFFFFA070, mouseX+offset, mouseY);
            }
            Component add = Component.translatable("screen.brnquest.editor.dependency.add");
            host.button(graphics, dependencyAddBounds().translated(offset, 0),
                    EditorButton.Definition.iconAndText(add, add, QuestActionIcons.named("plus")),
                    !ClientEditorState.get().busy(), EditorButton.Tone.PRIMARY, mouseX, mouseY);
            host.button(graphics, dependencyDoneBounds().translated(offset, 0), EditorButton.Definition.text(Component.translatable("gui.done"), null), true, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        } finally {
            graphics.pose().popPose();
        }
    }

    boolean click(double mouseX, double mouseY, int button) {
        if (button != 0 && button != 1) return true;
        if (button == 0 && dependencyDoneBounds().contains(mouseX, mouseY)) {
            close();
            return true;
        }
        if (!dependencyListInputReady()) return true;
        if (dependencyList.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0 && dependencyAddBounds().contains(mouseX, mouseY)) {
            if (!ClientEditorState.get().busy()) openDependencyPicker();
            return true;
        }
        if (dependencyActions.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 1 && !ClientEditorState.get().busy()) {
            dependencyList.rowAt(mouseX, mouseY).ifPresent(row ->
                    host.context(row.key(), (int) mouseX, (int) mouseY));
        }
        return true;
    }

    private boolean dependencyListInputReady() {
        return dependencyEditorOpen && dependencyListSnapshot != null && dependencyListSnapshot == host.snapshot()
                && ClientEditorState.get().editing() && host.selected() != null
                && host.selected().id().equals(dependencyEditorQuestId)
                && editorOverlays.active() == EditorOverlayHost.Kind.NONE
                && host.panelReady();
    }

    private void resetDependencyList() {
        dependencyList.reset();
        dependencyActions.clear();
        dependencyListSnapshot = null;
    }

    void open(QuestDefinition quest) {
        host.closeOverlay();
        dependencyEditorOpen = true;
        dependencyEditorQuestId = quest.id();
        resetDependencyList();
        dependencyEditorMessage = null;
    }

    void close() {
        dependencyEditorOpen = false;
        dependencyEditorQuestId = null;
        resetDependencyList();
        dependencyEditorMessage = null;
        if (editorOverlays.isOpen(EditorOverlayHost.Kind.DEPENDENCY_PICKER)) host.closeOverlay();
    }

    private void openDependencyPicker() {
        if (!dependencyEditorOpen || dependencyEditorQuestId == null) return;
        host.closeOverlay();
        dependencyFilter = "";
        dependencyPicker.reset();
        dependencyEditorMessage = null;
        editorOverlays.show(EditorOverlayHost.Kind.DEPENDENCY_PICKER);
    }

    void renderPicker(GuiGraphics graphics, int mouseX, int mouseY) {
        List<QuestDependencyEditorModel.Candidate> candidates = dependencyCandidates();
        UiRect bounds = dependencyPickerBounds();
        Component searchText = dependencyFilter.isBlank()
                ? Component.translatable("screen.brnquest.editor.dependency.search_hint") : Component.literal(dependencyFilter);
        graphics.fill(0, 0, width(), height(), 0x66000000);
        dependencyPicker.advance(bounds, new UiRect(0, topToolbarHeight(), width(), height() - bottomToolbarHeight()),
                candidates.size(), candidates::get, host.elapsed(), scrollSmoothSpeed());
        List<Component> tooltip = dependencyPicker.render(graphics, font, searchText, dependencyFilter.isBlank(),
                candidate -> new EditorPickerList.Entry(
                        Component.literal((candidate.createsCycle() ? "⚠ " : "") + candidate.title()),
                        dependencyCandidateSubtitle(candidate),
                        candidate.createsCycle() ? EditorPickerList.Tone.WARNING : EditorPickerList.Tone.NORMAL,
                        false, List.of(Component.translatable(candidate.createsCycle()
                                ? "screen.brnquest.editor.dependency.cycle_warning" : "screen.brnquest.editor.dependency.click_to_add"))),
                Component.translatable("screen.brnquest.editor.dependency.no_candidates"), mouseX, mouseY);
        if (!tooltip.isEmpty()) host.tooltip(tooltip);
    }

    static Component dependencyCandidateSubtitle(QuestDependencyEditorModel.Candidate candidate) {
        if (!candidate.createsCycle()) {
            return Component.literal(candidate.chapterTitle());
        }
        return Component.translatable("screen.brnquest.editor.dependency.cycle_warning");
    }

    boolean clickPicker(double mouseX, double mouseY, int button) {
        if (button != 0) {
            host.closeOverlay();
            return true;
        }
        UiRect bounds = dependencyPickerBounds();
        if (!bounds.contains(mouseX, mouseY)) {
            host.closeOverlay();
            return true;
        }
        if (dependencyPicker.mouseClicked(mouseX, mouseY, button)) return true;
        var shown = dependencyPicker.entryAt(mouseX, mouseY).orElse(null);
        if (shown == null) return true;
        // Recheck current dependencies/cycles by the displayed ID, not by an index into a freshly filtered list.
        var candidate = dependencyCandidates().stream().filter(value -> value.questId().equals(shown.questId()))
                .findFirst().orElse(null);
        if (candidate == null) return true;
        if (candidate.createsCycle()) {
            dependencyEditorMessage = Component.translatable("screen.brnquest.editor.dependency.cycle_warning");
            return true;
        }
        if (!ClientEditorState.get().busy()) {
            host.change(new Submission(false, dependencyEditorQuestId, candidate.questId()));
            dependencyEditorMessage = null;
            host.closeOverlay();
        }
        return true;
    }

    private List<QuestDependencyEditorModel.Candidate> dependencyCandidates() {
        QuestBookSnapshot snapshot = host.snapshot();
        if (snapshot == null || dependencyEditorQuestId == null) return List.of();
        return QuestDependencyEditorModel.candidates(snapshot.book(), dependencyEditorQuestId, dependencyFilter,
                host.locale());
    }

    private UiRect dependencyPickerBounds() {
        int maximumHeight = Math.max(EditorPickerList.SEARCH_HEIGHT + EditorPickerList.ROW_HEIGHT + 4,
                height() - topToolbarHeight() - bottomToolbarHeight() - 16);
        int desiredHeight = EditorPickerList.SEARCH_HEIGHT + EditorPickerList.ROW_HEIGHT * 8 + 4;
        return host.layout().centeredDialog(480, 300, 20, Math.min(desiredHeight, maximumHeight)).intersection(pickerScreenBounds());
    }

    private int dependencyListTop() {
        return topToolbarHeight() + 44;
    }

    private UiRect dependencyListBounds() {
        // Keep the existing whole-row layout, allowing only a partial row when the window is very short.
        int maximumBottom = Math.max(topToolbarHeight(), dependencyAddBounds().top() - 16);
        int top = Math.min(dependencyListTop(), maximumBottom);
        int available = maximumBottom - top;
        int listHeight = available >= DEPENDENCY_ROW_HEIGHT ? available / DEPENDENCY_ROW_HEIGHT * DEPENDENCY_ROW_HEIGHT : available;
        return new UiRect(detailLeft() + 10, top, width() - 10, top + listHeight);
    }

    private UiRect dependencyAddBounds() {
        return host.layout().dependencyFooterButton(0);
    }

    private UiRect dependencyDoneBounds() {
        return host.layout().dependencyFooterButton(1);
    }

    private int width() { return host.layout().width(); }
    private int height() { return host.layout().height(); }
    private int detailLeft() { return host.layout().detailLeft(); }
    private int detailsWidth() { return host.layout().detailsWidth(); }
    private int topToolbarHeight() { return host.layout().topToolbarHeight(); }
    private int bottomToolbarHeight() { return host.layout().bottomToolbarHeight(); }
    private UiRect pickerScreenBounds() { return new UiRect(0, topToolbarHeight(), width(), height() - bottomToolbarHeight()); }
    private static double scrollStep() { return BrnQuestClientConfig.read(BrnQuestClientConfig.VALUES.scrollStep); }
    private static double scrollSmoothSpeed() { return BrnQuestClientConfig.read(BrnQuestClientConfig.VALUES.smoothSpeed); }
}
