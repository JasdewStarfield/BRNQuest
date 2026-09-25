package yourscraft.jasdewstarfield.brnquest.client.ui;

import yourscraft.jasdewstarfield.brnquest.client.ui.component.GraystonePalette;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButton;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.QuestActionIcons;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButtonInput;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPropertyFormLayout;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPropertyPanel;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPropertyRow;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorSmoothScroll;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorTextField;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
import yourscraft.jasdewstarfield.brnquest.config.BrnQuestClientConfig;
import yourscraft.jasdewstarfield.brnquest.data.DependencyRequirement;
import yourscraft.jasdewstarfield.brnquest.data.QuestBehavior;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Scrollable behavior form composed from the same property rows, fields, buttons and scrollbar as the main editor. */
public final class EditorQuestBehaviorScreen extends Screen {
    private static final int ROW_HEIGHT = 24;
    private static final int ROW_COUNT = 14;
    private static final List<String> BOOLEAN_KEYS = List.of(
            "hide_until_dependencies_visible", "hide_until_dependencies_complete", "invisible_until_complete",
            "hide_details_until_startable", "hide_text_until_complete", "hide_lock_icon",
            "sequential_tasks", "repeatable", "ignore_reward_blocking", "require_all_team_members");

    // Shared input feedback follows the same rendered geometry as every form button.
    private final EditorButtonInput buttons = new EditorButtonInput();
    private final Screen parent;
    private final Consumer<QuestBehavior> consumer;
    // Null remains the explicit chapter-inheritance state; apply commits through the parent form.
    private Boolean hideDependencyLines;
    private final Consumer<Boolean> hideLinesSelection;
    private int rowCount() { return ROW_COUNT + (hideLinesSelection == null ? 0 : 1); }
    private final List<Boolean> booleans = new ArrayList<>();
    private final EditorSmoothScroll scroll = new EditorSmoothScroll();
    private DependencyRequirement requirement;
    private EditorTextField visibleAfterTasks;
    private EditorTextField minimumDependencies;
    private EditorTextField cooldownSeconds;
    private int visibleAfterTasksValue;
    private int minimumDependenciesValue;
    private int cooldownSecondsValue;
    private boolean requirementDropdownOpen;
    private long previousFrameNanos;
    private double renderedScroll;

    public EditorQuestBehaviorScreen(Screen parent, QuestBehavior value, Consumer<QuestBehavior> consumer) {
        this(parent, value, consumer, null, null);
    }
    public EditorQuestBehaviorScreen(Screen parent, QuestBehavior value, Consumer<QuestBehavior> consumer,
                                    Boolean hideDependencyLines, Consumer<Boolean> hideLinesSelection) {
        super(Component.translatable("screen.brnquest.editor.behavior.title"));
        this.hideDependencyLines = hideDependencyLines;
        this.hideLinesSelection = hideLinesSelection;
        this.parent = parent;
        this.consumer = consumer;
        booleans.addAll(List.of(value.hideUntilDependenciesVisible(), value.hideUntilDependenciesComplete(),
                value.invisibleUntilComplete(), value.hideDetailsUntilStartable(), value.hideTextUntilComplete(),
                value.hideLockIcon(), value.sequentialTasks(), value.repeatable(), value.ignoreRewardBlocking(), value.requireAllTeamMembers()));
        requirement = value.dependencyRequirement();
        visibleAfterTasksValue = value.visibleAfterTasks();
        minimumDependenciesValue = value.minimumRequiredDependencies();
        cooldownSecondsValue = value.repeatCooldownSeconds();
    }

    @Override
    protected void init() {
        buttons.begin(); buttons.clearFocus();
        if (visibleAfterTasks != null && parse(visibleAfterTasks) != null) visibleAfterTasksValue = parse(visibleAfterTasks);
        if (minimumDependencies != null && parse(minimumDependencies) != null) minimumDependenciesValue = parse(minimumDependencies);
        if (cooldownSeconds != null && parse(cooldownSeconds) != null) cooldownSecondsValue = parse(cooldownSeconds);
        visibleAfterTasks = numberEditor("visible_after_tasks", visibleAfterTasksValue);
        minimumDependencies = numberEditor("minimum_required_dependencies", minimumDependenciesValue);
        cooldownSeconds = numberEditor("repeat_cooldown_seconds", cooldownSecondsValue);
        // Fields are rendered explicitly inside the foreground scissor and must not be redrawn at base depth.
        addWidget(visibleAfterTasks);
        addWidget(minimumDependencies);
        addWidget(cooldownSeconds);
    }

    private EditorTextField numberEditor(String key, int value) {
        EditorTextField editor = new EditorTextField(font,
                Component.translatable("screen.brnquest.editor.behavior." + key), 10);
        editor.setValue(Integer.toString(value));
        editor.setFilter(text -> text.isEmpty() || text.matches("[0-9]{0,10}"));
        return editor;
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}
    @Override public void tick() { parent.tick(); super.tick(); }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        buttons.begin();
        ChildScreenBackground.render(parent, graphics, width, height, partialTick);
        graphics.pose().pushPose();
        // Match the other modal editor surfaces: authored items use raised render depth, so the modal must be higher.
        graphics.pose().translate(0, 0, 500);
        graphics.fill(0, 0, width, height, GraystonePalette.BACKDROP);
        UiRect panel = panel();
        EditorPropertyPanel.renderGraystone(graphics, font,
                new EditorPropertyPanel.Layout(panel, panel.left() + 12, panel.width() - 24,
                        panel.top() + 9, panel.top() + 30, ROW_HEIGHT),
                title, 0xFFFFFFFF, List.of(),
                new EditorPropertyPanel.Footer(cancelBounds(), applyBounds(), Component.translatable("screen.brnquest.editor.scope.apply_parent"),
                        valid(), EditorButton.Tone.PRIMARY),
                (target, bounds, text, enabled, tone) -> buttons.render(target, font, bounds,
                        EditorButton.Definition.text(text, null), enabled, false, tone, mouseX, mouseY));

        UiRect viewport = viewport();
        long now = System.nanoTime();
        double elapsed = previousFrameNanos == 0 ? 1.0 / 60.0
                : Math.min(0.1, Math.max(0.0, (now - previousFrameNanos) / 1_000_000_000.0));
        previousFrameNanos = now;
        renderedScroll = scroll.frameAndRender(graphics, viewport.right() + 2, viewport.top(), viewport.bottom(),
                contentHeight(), viewport.height(), elapsed, BrnQuestClientConfig.VALUES.smoothSpeed.get());

        hideNumberFields();
        buttons.viewport(viewport, 0);
        graphics.enableScissor(viewport.left(), viewport.top(), viewport.right(), viewport.bottom());
        // Section headings belong to the same scroll content as their fields.
        int[] starts = {0, 7, 9};
        String[] sections = {"visibility", "dependencies", "completion"};
        for (int i = 0; i < starts.length; i++) {
            var bounds = rowBounds(starts[i]);
            EditorPropertyPanel.section(font, "screen.brnquest.editor.section." + sections[i])
                    .render(graphics, bounds.left(), bounds.top() - 24, bounds.width());
        }
        for (int row = 0; row < rowCount(); row++) {
            UiRect bounds = rowBounds(row);
            if (bounds.bottom() <= viewport.top() || bounds.top() >= viewport.bottom()) continue;
            int booleanIndex = booleanIndexAtRow(row);
            if (row == ROW_COUNT) {
                var layout = propertyRow(bounds);
                EditorPropertyRow.label(graphics, font, Component.translatable("screen.brnquest.quest.hide_dependency_lines"), layout.label(), null);
                var label = Component.translatable(hideDependencyLines == null ? "screen.brnquest.dependency_lines.inherit"
                        : hideDependencyLines ? "screen.brnquest.dependency_lines.hide" : "screen.brnquest.dependency_lines.show");
                buttons.render(graphics, font, layout.field(), EditorButton.Definition.text(label, null),
                        true, false, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
            } else if (booleanIndex >= 0) renderBoolean(graphics, booleanIndex, bounds, mouseX, mouseY);
            else if (row == 3) renderNumber(graphics, visibleAfterTasks, "visible_after_tasks", bounds, mouseX, mouseY, partialTick);
            else if (row == 7) renderRequirement(graphics, bounds, mouseX, mouseY);
            else if (row == 8) renderNumber(graphics, minimumDependencies, "minimum_required_dependencies", bounds, mouseX, mouseY, partialTick);
            else if (row == 11) renderNumber(graphics, cooldownSeconds, "repeat_cooldown_seconds", bounds, mouseX, mouseY, partialTick);
        }
        graphics.disableScissor();
        buttons.viewport(null, 0);
        if (requirementDropdownOpen) renderRequirementDropdown(graphics, mouseX, mouseY);
        graphics.pose().popPose();
    }

    private void renderBoolean(GuiGraphics graphics, int index, UiRect bounds, int mouseX, int mouseY) {
        EditorPropertyFormLayout.Row layout = propertyRow(bounds);
        EditorPropertyRow.label(graphics, font,
                Component.translatable("screen.brnquest.editor.behavior." + BOOLEAN_KEYS.get(index)),
                layout.label(), null);
        buttons.render(graphics, font, layout.field(), EditorButton.Definition.text(
                        Component.translatable(booleans.get(index) ? "options.on" : "options.off"), null),
                true, false, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
    }

    private void renderNumber(GuiGraphics graphics, EditorTextField editor, String key, UiRect bounds,
                              int mouseX, int mouseY, float partialTick) {
        EditorPropertyRow.text(graphics, font, propertyRow(bounds),
                Component.translatable("screen.brnquest.editor.behavior." + key), null, editor, true);
        editor.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderRequirement(GuiGraphics graphics, UiRect bounds, int mouseX, int mouseY) {
        EditorPropertyFormLayout.Row layout = propertyRow(bounds);
        EditorPropertyRow.label(graphics, font,
                Component.translatable("screen.brnquest.editor.behavior.dependency_requirement"),
                layout.label(), null);
        buttons.render(graphics, font, layout.field(), EditorButton.Definition.iconAndText(
                        Component.translatable("screen.brnquest.editor.value.dependency." + requirement.serializedName()), null,
                        QuestActionIcons.named(requirementDropdownOpen ? "fold" : "unfold")),
                true, false, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
    }

    private void renderRequirementDropdown(GuiGraphics graphics, int mouseX, int mouseY) {
        // Only the foreground menu owns feedback while it covers form controls.
        buttons.begin();
        UiRect menu = dropdownBounds();
        DependencyRequirement[] values = DependencyRequirement.values();
        for (int index = 0; index < values.length; index++) {
            UiRect option = new UiRect(menu.left(), menu.top() + index * 20,
                    menu.right(), menu.top() + (index + 1) * 20);
            buttons.render(graphics, font, option,
                    EditorButton.Definition.text(Component.translatable("screen.brnquest.editor.value.dependency." + values[index].serializedName()), null),
                    true, values[index] == requirement, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        }
    }

    private void hideNumberFields() {
        visibleAfterTasks.hide();
        minimumDependencies.hide();
        cooldownSeconds.hide();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        buttons.clicked(mouseX, mouseY, button);
        if (button == 0 && requirementDropdownOpen) {
            int option = dropdownOptionAt(mouseX, mouseY);
            if (option >= 0) {
                requirement = DependencyRequirement.values()[option];
                requirementDropdownOpen = false;
                return true;
            }
            requirementDropdownOpen = false;
            // Dismissing the foreground menu must not activate a covered form button.
            return true;
        }
        if (button == 0 && cancelBounds().contains(mouseX, mouseY)) { onClose(); return true; }
        if (button == 0 && applyBounds().contains(mouseX, mouseY) && valid()) { apply(); return true; }
        UiRect viewport = viewport();
        if (button == 0 && scroll.handleTrackClick(mouseX, mouseY, viewport.right() + 2,
                viewport.top(), viewport.bottom(), contentHeight(), viewport.height())) return true;
        if (button == 0 && viewport.contains(mouseX, mouseY)) {
            int row = -1;
            for (int candidate = 0; candidate < rowCount(); candidate++) {
                if (rowBounds(candidate).contains(mouseX, mouseY)) { row = candidate; break; }
            }
            if (row == ROW_COUNT && propertyRow(rowBounds(row)).field().contains(mouseX, mouseY)) {
                hideDependencyLines = hideDependencyLines == null ? Boolean.TRUE : hideDependencyLines ? Boolean.FALSE : null;
                return true;
            }
            if (row >= 0) {
                int booleanIndex = booleanIndexAtRow(row);
                if (booleanIndex >= 0 && propertyRow(rowBounds(row)).field().contains(mouseX, mouseY)) {
                    booleans.set(booleanIndex, !booleans.get(booleanIndex));
                    return true;
                }
                if (row == 7 && propertyRow(rowBounds(row)).field().contains(mouseX, mouseY)) {
                    requirementDropdownOpen = true;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        UiRect viewport = viewport();
        if (viewport.contains(mouseX, mouseY)) {
            scroll.scrollWheel(scrollY, BrnQuestClientConfig.VALUES.scrollStep.get(),
                    contentHeight(), viewport.height());
            requirementDropdownOpen = false;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return scroll.handleDrag(mouseY, button)
                || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return scroll.handleRelease(button) || super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean valid() {
        return parse(visibleAfterTasks) != null && parse(minimumDependencies) != null
                && parse(cooldownSeconds) != null;
    }

    private void apply() {
        consumer.accept(new QuestBehavior(booleans.get(0), booleans.get(1), booleans.get(2), parse(visibleAfterTasks),
                booleans.get(3), booleans.get(4), booleans.get(5), requirement, parse(minimumDependencies),
                booleans.get(6), booleans.get(7), parse(cooldownSeconds), booleans.get(8), booleans.get(9)));
        if (hideLinesSelection != null) hideLinesSelection.accept(hideDependencyLines);
        onClose();
    }

    private Integer parse(EditorTextField editor) {
        try { return Integer.parseInt(editor.getValue()); }
        catch (NumberFormatException exception) { return null; }
    }

    private int dropdownOptionAt(double mouseX, double mouseY) {
        UiRect menu = dropdownBounds();
        if (!menu.contains(mouseX, mouseY)) return -1;
        int option = (int) ((mouseY - menu.top()) / 20);
        return option >= 0 && option < DependencyRequirement.values().length ? option : -1;
    }

    private UiRect dropdownBounds() {
        UiRect anchor = propertyRow(rowBounds(7)).field();
        int height = DependencyRequirement.values().length * 20;
        int below = anchor.bottom() + 1;
        int top = below + height <= viewport().bottom() ? below : anchor.top() - height - 1;
        top = Math.max(viewport().top(), Math.min(top, viewport().bottom() - height));
        return new UiRect(anchor.left(), top, anchor.right(), top + height);
    }

    private int booleanIndexAtRow(int row) {
        return switch (row) {
            case 0, 1, 2 -> row;
            case 4, 5, 6 -> row - 1;
            case 9, 10 -> row - 3;
            case 12 -> 8;
            case 13 -> 9;
            default -> -1;
        };
    }

    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(parent); }

    private UiRect panel() {
        int panelWidth = Math.min(680, Math.max(280, width - 24));
        int panelHeight = Math.min(500, Math.max(220, height - 24));
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        return new UiRect(left, top, left + panelWidth, top + panelHeight);
    }

    private UiRect viewport() {
        UiRect panel = panel();
        return new UiRect(panel.left() + 12, panel.top() + 30, panel.right() - 17, panel.bottom() - 46);
    }

    private UiRect rowBounds(int row) {
        UiRect viewport = viewport();
        int top = viewport.top() + row * ROW_HEIGHT + 24 * (1 + (row >= 7 ? 1 : 0) + (row >= 9 ? 1 : 0)) - (int) Math.round(renderedScroll);
        return new UiRect(viewport.left(), top, viewport.right(), top + EditorPropertyFormLayout.FIELD_HEIGHT);
    }

    private EditorPropertyFormLayout.Row propertyRow(UiRect bounds) {
        int labelWidth = Math.max(120, bounds.width() - 210);
        return EditorPropertyFormLayout.row(bounds.left(), bounds.top(), bounds.width(), labelWidth);
    }

    private int contentHeight() { return rowCount() * ROW_HEIGHT + 72; }
    private UiRect cancelBounds() { UiRect p=panel(); return new UiRect(p.left()+12,p.bottom()-34,p.centerX()-4,p.bottom()-10); }
    private UiRect applyBounds() { UiRect p=panel(); return new UiRect(p.centerX()+4,p.bottom()-34,p.right()-12,p.bottom()-10); }
    /** Child editors follow the task book's pause policy instead of Screen's unconditional default. */
    @Override public boolean isPauseScreen() { return parent != null && parent.isPauseScreen(); }

}
