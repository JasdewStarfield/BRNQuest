package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractWidget;
import yourscraft.jasdewstarfield.brnquest.config.BrnQuestClientConfig;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.*;
import yourscraft.jasdewstarfield.brnquest.data.QuestCreationDefaults;
import java.util.*;
import java.util.function.Consumer;

/** Sparse template form: blank numbers and the default toggle state remove an override. */
final class EditorCreationDefaultsScreen extends Screen {
    private final Screen parent;
    private final Consumer<QuestCreationDefaults> selection;
    private final QuestCreationDefaults inherited;
    // This chapter-wide inherited policy is staged beside defaults without changing its stored meaning.
    private boolean hideDependencyLines;
    private final Consumer<Boolean> hideLinesSelection;
    private final List<List<String>> groups;
    private final Map<String, String> values = new TreeMap<>();
    private EditorButtonWidget done;
    private final List<AbstractWidget> controls = new ArrayList<>();
    private final EditorSmoothScroll scroll = new EditorSmoothScroll();
    private double renderedScroll;
    private long previousFrameNanos;
    private static final int ROW_HEIGHT = 24;
    private static final int SECTION_HEIGHT = 24;
    private static final List<String> SECTIONS = List.of("appearance", "visibility", "dependencies", "completion");
    // Keep related fields together while retaining every sparse-template override.
    static final List<List<String>> GROUPS = List.of(
            List.of("shape", "size", "icon_scale", "min_width"),
            List.of("hide_until_dependencies_visible", "hide_until_dependencies_complete", "invisible_until_complete",
                    "visible_after_tasks", "hide_details_until_startable", "hide_text_until_complete", "hide_lock_icon"),
            List.of("dependency_requirement"),
            List.of("sequential_tasks", "repeatable", "repeat_cooldown_seconds", "ignore_reward_blocking", "require_all_team_members"));
    EditorCreationDefaultsScreen(Screen parent, QuestCreationDefaults initial, QuestCreationDefaults inherited,
                                 Consumer<QuestCreationDefaults> selection) {
        this(parent, initial, inherited, selection, false, null);
    }
    EditorCreationDefaultsScreen(Screen parent, QuestCreationDefaults initial, QuestCreationDefaults inherited,
                                 Consumer<QuestCreationDefaults> selection, boolean hideDependencyLines,
                                 Consumer<Boolean> hideLinesSelection) {
        super(Component.translatable("screen.brnquest.defaults.title"));
        this.hideDependencyLines = hideDependencyLines;
        this.hideLinesSelection = hideLinesSelection;
        groups = new ArrayList<>(GROUPS);
        if (hideLinesSelection != null) groups.set(2, List.of("dependency_requirement", "hide_dependency_lines"));
        this.parent = parent; this.selection = selection; this.inherited = inherited;
        values.putAll(initial.values());
    }
    @Override protected void init() {
        controls.clear();
        int half = fieldWidth(), left = viewport().right() - half;
        for (String key : groups.stream().flatMap(List::stream).toList()) {
            int y = 0;
            if (key.equals("hide_dependency_lines")) {
                addControl(new EditorButtonWidget(left, y, half, 20, hideLinesLabel(), button -> {
                    hideDependencyLines = !hideDependencyLines;
                    button.setMessage(hideLinesLabel());
                }));
            } else if (key.equals("shape")) {
                addControl(new EditorButtonWidget(left, y, half, 20, shapeLabel(), button -> {
                    var shapes = List.of("", "chamfer", "square", "circle", "diamond");
                    String next = shapes.get((shapes.indexOf(values.getOrDefault("shape", "")) + 1) % shapes.size());
                    if (next.isEmpty()) values.remove("shape"); else values.put("shape", next);
                    button.setMessage(shapeLabel()); validate();
                }));
            } else if (key.equals("dependency_requirement")) {
                addControl(new EditorButtonWidget(left, y, half, 20, dependencyLabel(), button -> {
                    var modes = List.of("", "all_completed", "one_completed", "all_started", "one_started");
                    String next = modes.get((modes.indexOf(values.getOrDefault(key, "")) + 1) % modes.size());
                    if (next.isEmpty()) values.remove(key); else values.put(key, next);
                    button.setMessage(dependencyLabel()); validate();
                }));
            } else if (QuestCreationDefaults.numberField(key)) {
                var input = new EditBox(font, left, y, half, 20, label(key));
                input.setMaxLength(128); input.setValue(values.getOrDefault(key, ""));
                input.setHint(Component.literal(inherited.values().getOrDefault(key, coreValue(key))));
                input.setResponder(value -> { if (value.isBlank()) values.remove(key); else values.put(key, value); validate(); });
                addControl(input);
            } else {
                addControl(new EditorButtonWidget(left, y, half, 20, toggleLabel(key), button -> {
                    String value = values.get(key);
                    if (value == null) values.put(key, "true");
                    else if (value.equals("true")) values.put(key, "false"); else values.remove(key);
                    button.setMessage(toggleLabel(key)); validate();
                }));
            }
        }
        UiRect cancel = cancelBounds(), apply = applyBounds();
        addRenderableWidget(new EditorButtonWidget(cancel.left(), cancel.top(), cancel.width(), cancel.height(),
                Component.translatable("gui.cancel"), button -> onClose()));
        done = addRenderableWidget(new EditorButtonWidget(apply.left(), apply.top(), apply.width(), apply.height(),
                Component.translatable("screen.brnquest.editor.scope.apply_parent"), button -> {
            selection.accept(new QuestCreationDefaults(values));
            if (hideLinesSelection != null) hideLinesSelection.accept(hideDependencyLines);
            onClose();
        }));
        validate();
    }
    private Component hideLinesLabel() {
        return Component.translatable(hideDependencyLines ? "options.on" : "options.off");
    }
    private Component shapeLabel() {
        String shape = values.get("shape");
        return shape == null ? Component.translatable("screen.brnquest.defaults.inherit", Component.translatable(
                "screen.brnquest.editor.value.shape." + inherited.values().getOrDefault("shape", "chamfer")))
                : Component.translatable("screen.brnquest.editor.value.shape." + shape);
    }

    private Component dependencyLabel() {
        String value = values.get("dependency_requirement");
        Component mode = Component.translatable("screen.brnquest.defaults.dependency." + (value == null
                ? inherited.values().getOrDefault("dependency_requirement", "all_completed") : value));
        return value == null ? Component.translatable("screen.brnquest.defaults.inherit", mode) : mode;
    }

    private String coreValue(String key) {
        if (QuestCreationDefaults.integerField(key)) return "0";
        return switch (key) { case "shape" -> "chamfer"; case "min_width" -> "0.0"; default -> "1.0"; };
    }
    private Component label(String key) { return Component.translatable(key.equals("hide_dependency_lines")
            ? "screen.brnquest.quest.hide_dependency_lines" : "screen.brnquest.defaults." + key); }
    private Component toggleLabel(String key) {
        String value = values.get(key);
        return value == null ? Component.translatable("screen.brnquest.defaults.inherit", Component.translatable(
                inherited.values().getOrDefault(key, "false").equals("true") ? "options.on" : "options.off"))
                : Component.translatable(value.equals("true") ? "options.on" : "options.off");
    }
    private void validate() {
        if (done == null) return;
        try { new QuestCreationDefaults(values); done.active = true; }
        catch (IllegalArgumentException exception) { done.active = false; }
    }
    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float partial) {}
    /** Form controls render only inside the viewport; footer widgets remain fixed. */
    private void addControl(AbstractWidget widget) {
        controls.add(widget);
        addWidget(widget);
    }

    @Override public void render(GuiGraphics graphics, int x, int y, float partial) {
        ChildScreenBackground.render(parent, graphics, width, height, partial);
        graphics.pose().pushPose(); graphics.pose().translate(0, 0, 1000);
        try {
            UiRect panel = panel(), viewport = viewport();
            graphics.fill(0, 0, width, height, GraystonePalette.BACKDROP);
            GraystoneSurface.raised(graphics, panel, GraystonePalette.PANEL, true);
            graphics.drawCenteredString(font, title, width / 2, panel.top() + 9, 0xFFFFFFFF);
            graphics.drawCenteredString(font, Component.translatable("screen.brnquest.defaults.help"),
                    width / 2, panel.top() + 25, GraystonePalette.SECONDARY);
            long now = System.nanoTime();
            double elapsed = previousFrameNanos == 0 ? 1.0 / 60.0
                    : Math.min(0.1, Math.max(0.0, (now - previousFrameNanos) / 1_000_000_000.0));
            previousFrameNanos = now;
            renderedScroll = scroll.frameAndRender(graphics, viewport.right() + 2, viewport.top(), viewport.bottom(),
                    contentHeight(), viewport.height(), elapsed, BrnQuestClientConfig.VALUES.smoothSpeed.get());
            graphics.enableScissor(viewport.left(), viewport.top(), viewport.right(), viewport.bottom());
            try {
                int top = viewport.top() - (int) Math.round(renderedScroll), row = 0;
                for (int group = 0; group < groups.size(); group++) {
                    EditorPropertyPanel.section(font, "screen.brnquest.editor.section." + SECTIONS.get(group))
                            .render(graphics, viewport.left(), top, viewport.width());
                    top += SECTION_HEIGHT;
                    for (String key : groups.get(group)) {
                        AbstractWidget control = controls.get(row++);
                        control.setX(viewport.right() - fieldWidth());
                        control.setY(top);
                        control.visible = top + 20 > viewport.top() && top < viewport.bottom();
                        if (control.visible) {
                            EditorPropertyRow.label(graphics, font, label(key),
                                    new UiRect(viewport.left(), top, control.getX() - 6, top + 20), null);
                            control.render(graphics, x, viewport.contains(x, y) ? y : -1, partial);
                        }
                        top += ROW_HEIGHT;
                    }
                }
            } finally { graphics.disableScissor(); }
            if (!done.active) graphics.drawCenteredString(font, Component.translatable("screen.brnquest.defaults.invalid"),
                    width / 2, panel.bottom() - 45, 0xFFFF8080);
            super.render(graphics, x, y, partial); graphics.flush();
        } finally { graphics.pose().popPose(); }
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        UiRect viewport = viewport();
        if (button == 0 && scroll.handleTrackClick(x, y, viewport.right() + 2, viewport.top(), viewport.bottom(),
                contentHeight(), viewport.height())) return true;
        // Clipped rows must never receive clicks through the title or fixed footer.
        if (viewport.contains(x, y) || cancelBounds().contains(x, y) || applyBounds().contains(x, y)) {
            if (!viewport.contains(x, y)) controls.forEach(control -> control.visible = false);
            return super.mouseClicked(x, y, button);
        }
        return false;
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (viewport().contains(x, y)) {
            scroll.scrollWheel(vertical, BrnQuestClientConfig.VALUES.scrollStep.get(), contentHeight(), viewport().height());
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return scroll.handleDrag(y, button) || super.mouseDragged(x, y, button, dx, dy);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        return scroll.handleRelease(button) || super.mouseReleased(x, y, button);
    }

    private UiRect panel() {
        int w = Math.min(680, width - 24), h = Math.min(500, height - 24);
        int left = (width - w) / 2, top = (height - h) / 2;
        return new UiRect(left, top, left + w, top + h);
    }
    private UiRect viewport() {
        UiRect p = panel();
        return new UiRect(p.left() + 12, p.top() + 42, p.right() - 17, p.bottom() - 48);
    }
    private int fieldWidth() { return Math.min(210, viewport().width() / 2); }
    private int contentHeight() { return groups.stream().mapToInt(List::size).sum() * ROW_HEIGHT + groups.size() * SECTION_HEIGHT; }
    private UiRect cancelBounds() { UiRect p = panel(); return new UiRect(p.left()+12,p.bottom()-34,p.centerX()-4,p.bottom()-10); }
    private UiRect applyBounds() { UiRect p = panel(); return new UiRect(p.centerX()+4,p.bottom()-34,p.right()-12,p.bottom()-10); }
    @Override public void tick() { parent.tick(); super.tick(); }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return parent.isPauseScreen(); }
}
