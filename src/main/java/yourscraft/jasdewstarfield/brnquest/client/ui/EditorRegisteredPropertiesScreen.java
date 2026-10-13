package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import yourscraft.jasdewstarfield.brnquest.config.BrnQuestClientConfig;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.*;
import yourscraft.jasdewstarfield.brnquest.data.QuestCreationDefaults;
import yourscraft.jasdewstarfield.brnquest.editor.QuestPropertySchema;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigValueType;
import java.util.*;
import java.util.function.Consumer;

/** Shared registered-property form; creation scopes add sparse inheritance to the instance controls. */
class EditorRegisteredPropertiesScreen extends Screen {
    private final Screen parent;
    private final Consumer<Map<String, String>> selection;
    private final boolean template;
    private final List<String> sections;
    private final EditorPropertyValues.Scope scope;
    private final EditorPropertyValues values;
    // This chapter-wide inherited policy is staged beside defaults without changing its stored meaning.
    private boolean hideDependencyLines;
    private final Consumer<Boolean> hideLinesSelection;
    // Item consumption is a chapter override staged with task defaults, not a QuestCreationDefaults field.
    private Boolean consumeItems;
    private final boolean inheritedConsumeItems;
    private final Consumer<Boolean> consumeSelection;
    private final List<List<String>> groups;
    private EditorButtonWidget done;
    private final List<AbstractWidget> controls = new ArrayList<>();
    private final EditorSmoothScroll scroll = new EditorSmoothScroll();
    private final EditorEnumDropdown enumDropdown = new EditorEnumDropdown();
    private double renderedScroll;
    private long previousFrameNanos;
    private static final int ROW_HEIGHT = 24;
    private static final int SECTION_HEIGHT = 24;
    // Both default scopes derive groups from the registered instance properties.
    static final List<List<String>> GROUPS = QuestPropertySchema.CATALOG.sections(QuestPropertySchema.CATALOG.defaults())
            .values().stream().map(fields -> fields.stream().map(QuestPropertySchema.Property::key).toList()).toList();
    EditorRegisteredPropertiesScreen(Screen parent, EditorPropertyValues.Scope scope,
                                 QuestCreationDefaults initial, QuestCreationDefaults inherited,
                                 Consumer<QuestCreationDefaults> selection, boolean hideDependencyLines,
                                 Consumer<Boolean> hideLinesSelection, Boolean consumeItems,
                                 boolean inheritedConsumeItems, Consumer<Boolean> consumeSelection) {
        this(parent, Component.translatable(scope == EditorPropertyValues.Scope.BOOK
                        ? "screen.brnquest.defaults.book_title" : "screen.brnquest.defaults.chapter_title"), initial.values(), inherited,
                QuestPropertySchema.CATALOG.defaults(), values -> selection.accept(new QuestCreationDefaults(values)), scope,
                hideDependencyLines, hideLinesSelection, consumeItems, inheritedConsumeItems, consumeSelection);
    }

    /** The instance form shares types, labels and validation; only its inheritance mode differs. */
    protected EditorRegisteredPropertiesScreen(Screen parent, Component title, Map<String, String> initial,
            List<QuestPropertySchema.Property> properties, Consumer<Map<String, String>> selection) {
        this(parent, title, initial, QuestCreationDefaults.EMPTY, properties, selection, EditorPropertyValues.Scope.INSTANCE,
                false, null, null, false, null);
    }

    private EditorRegisteredPropertiesScreen(Screen parent, Component title, Map<String, String> initial, QuestCreationDefaults inherited,
            List<QuestPropertySchema.Property> properties, Consumer<Map<String, String>> selection, EditorPropertyValues.Scope scope,
            boolean hideDependencyLines, Consumer<Boolean> hideLinesSelection, Boolean consumeItems,
            boolean inheritedConsumeItems, Consumer<Boolean> consumeSelection) {
        super(title);
        this.parent = parent; this.selection = selection; this.scope = scope; this.template = scope.template();
        this.values = new EditorPropertyValues(scope, properties, initial, inherited.values());
        this.hideDependencyLines = hideDependencyLines; this.hideLinesSelection = hideLinesSelection;
        this.consumeItems = consumeItems; this.inheritedConsumeItems = inheritedConsumeItems; this.consumeSelection = consumeSelection;
        var grouped = QuestPropertySchema.CATALOG.sections(properties);
        sections = new ArrayList<>(grouped.keySet());
        groups = new ArrayList<>(grouped.values().stream()
                .map(fields -> fields.stream().map(QuestPropertySchema.Property::key).toList()).toList());
        // This is a live display rule for existing quests, separate from one-time creation presets.
        if (hideLinesSelection != null) addSpecialRow("live_display", "hide_dependency_lines");
        if (consumeSelection != null) addSpecialRow("completion", "consume_items");
    }
    private void addSpecialRow(String section, String key) {
        int index = sections.indexOf(section);
        if (index < 0) { sections.add(section); groups.add(new ArrayList<>()); index = groups.size() - 1; }
        var fields = new ArrayList<>(groups.get(index)); fields.add(key); groups.set(index, List.copyOf(fields));
    }

    @Override protected void init() {
        controls.clear();
        int half = fieldWidth(), left = viewport().right() - half;
        for (String key : groups.stream().flatMap(List::stream).toList()) {
            int y = 0;
            if (key.equals("hide_dependency_lines") && hideLinesSelection != null) {
                addControl(new EditorButtonWidget(left, y, half, 20, hideLinesLabel(), button -> {
                    hideDependencyLines = !hideDependencyLines; button.setMessage(hideLinesLabel());
                }));
                controls.getLast().setTooltip(Tooltip.create(Component.translatable("screen.brnquest.defaults.policy.chapter_default")));
            } else if (key.equals("consume_items") && consumeSelection != null) {
                addControl(new EditorButtonWidget(left, y, half, 20, consumeItemsLabel(), button -> {
                    consumeItems = consumeItems == null ? Boolean.TRUE : consumeItems ? Boolean.FALSE : null;
                    button.setMessage(consumeItemsLabel());
                }));
                controls.getLast().setTooltip(Tooltip.create(Component.translatable("screen.brnquest.defaults.policy.consume")));
            } else {
                var property = QuestPropertySchema.CATALOG.property(key);
                var field = property.field();
                if (!field.allowedValues().isEmpty()) {
                    var choices = new ArrayList<String>();
                    if (template) choices.add("");
                    choices.addAll(field.allowedValues());
                    addControl(new EditorButtonWidget(left, y, half, 20,
                            EditorButton.Definition.iconAndText(choiceLabel(key, values.value(key)), null, QuestActionIcons.named("unfold")), button ->
                            openChoice((EditorButtonWidget) button, choices, value -> choiceLabel(key, value), value -> {
                                values.edit(property, value);
                                button.setMessage(choiceLabel(key, values.value(key)));
                                button.setTooltip(sourceTooltip(property)); validate();
                            })));
                } else if (field.valueType() == ConfigValueType.BOOLEAN) {
                    addControl(new EditorButtonWidget(left, y, half, 20, choiceLabel(key, values.value(key)), button -> {
                        String value = values.value(key);
                        values.edit(property, value == null ? "true" : value.equals("true") ? "false" : template ? "" : "true");
                        button.setMessage(choiceLabel(key, values.value(key)));
                        button.setTooltip(sourceTooltip(property)); validate();
                    }));
                } else {
                    var input = new EditBox(font, left, y, half, 20, label(key));
                    input.setMaxLength(128); input.setValue(Objects.requireNonNullElse(values.value(key), ""));
                    // Vanilla hints use the input's text color unless the component supplies its own style.
                    input.setHint(Component.translatable(values.hintKey(property), values.fallback(property))
                            .withStyle(ChatFormatting.GRAY));
                    input.setResponder(value -> {
                        values.edit(property, value);
                        input.setTooltip(sourceTooltip(property));
                        validate();
                    });
                    addControl(input);
                }
                controls.getLast().setTooltip(sourceTooltip(property));
            }
        }
        UiRect cancel = cancelBounds(), apply = applyBounds();
        addRenderableWidget(new EditorButtonWidget(cancel.left(), cancel.top(), cancel.width(), cancel.height(),
                Component.translatable("gui.cancel"), button -> onClose()));
        done = addRenderableWidget(new EditorButtonWidget(apply.left(), apply.top(), apply.width(), apply.height(),
                Component.translatable("screen.brnquest.editor.scope.apply_parent"), button -> {
            selection.accept(values.confirmed());
            if (hideLinesSelection != null) hideLinesSelection.accept(hideDependencyLines);
            if (consumeSelection != null) consumeSelection.accept(consumeItems);
            onClose();
        }));
        validate();
    }
    private Component hideLinesLabel() {
        return Component.translatable(hideDependencyLines ? "options.on" : "options.off");
    }
    private Component consumeItemsLabel() {
        return consumeItems == null
                ? Component.translatable("screen.brnquest.defaults.inherit",
                        Component.translatable(inheritedConsumeItems ? "options.on" : "options.off"))
                : Component.translatable(consumeItems ? "options.on" : "options.off");
    }
    private void openChoice(EditorButtonWidget button, List<String> choices,
                            java.util.function.Function<String, Component> labels, Consumer<String> selection) {
        UiRect bounds = new UiRect(button.getX(), button.getY(), button.getX() + button.getWidth(), button.getY() + button.getHeight());
        enumDropdown.show(bounds, choices, labels, selection);
        button.setIcon(QuestActionIcons.named("fold"));
    }

    private void closeChoice() {
        enumDropdown.close();
        // Restore choice arrows without adding an icon to ordinary boolean controls.
        int index = 0;
        for (String key : groups.stream().flatMap(List::stream).toList()) {
            var control = controls.get(index++);
            if (control instanceof EditorButtonWidget button && !key.equals("consume_items")
                    && !(key.equals("hide_dependency_lines") && hideLinesSelection != null)
                    && !QuestPropertySchema.CATALOG.property(key).field().allowedValues().isEmpty())
                button.setIcon(QuestActionIcons.named("unfold"));
        }
    }
    private Component label(String key) {
        if (key.equals("consume_items")) return Component.translatable("screen.brnquest.defaults.consume_items");
        if (key.equals("hide_dependency_lines") && hideLinesSelection != null)
            return Component.translatable("screen.brnquest.defaults.chapter_dependency_lines");
        return Component.translatable(QuestPropertySchema.CATALOG.property(key).field().labelKey());
    }
    private Component choiceLabel(String key, String value) {
        var property = QuestPropertySchema.CATALOG.property(key);
        boolean inherit = template && (value == null || value.isEmpty());
        String effective = inherit ? values.fallback(property) : value;
        Component label = valueLabel(property, effective);
        return inherit ? Component.translatable(scope.defaultLabelKey(), label) : label;
    }
    private Component valueLabel(QuestPropertySchema.Property property, String effective) {
        return property.field().valueType() == ConfigValueType.BOOLEAN
                ? Component.translatable("true".equals(effective) ? "options.on" : "options.off")
                : property.field().valueLabelKeys().containsKey(effective)
                    ? Component.translatable(property.field().valueLabelKeys().get(effective)) : Component.literal(effective);
    }
    /** Explain the source and when it is resolved, without guessing a saved value's provenance. */
    private Tooltip sourceTooltip(QuestPropertySchema.Property property) {
        var text = Component.empty();
        if (template) {
            String key = switch (values.source(property)) {
                case SAVED -> "screen.brnquest.defaults.source.override";
                case BOOK -> "screen.brnquest.defaults.source.book";
                case BOOK_SYSTEM -> "screen.brnquest.defaults.source.book_system";
                case SYSTEM -> "screen.brnquest.defaults.source.system";
            };
            text.append(Component.translatable(key, valueLabel(property, Objects.requireNonNullElse(
                    values.value(property.key()), values.fallback(property)))));
            text.append("\n").append(Component.translatable("screen.brnquest.defaults.creation_help"));
        } else if (property.creationDefault()) {
            text.append(Component.translatable(values.source(property) == EditorPropertyValues.Source.SAVED
                    ? "screen.brnquest.defaults.source.saved" : "screen.brnquest.defaults.source.instance_system", values.fallback(property)));
            if (property.field().valueType() == ConfigValueType.INTEGER || property.field().valueType() == ConfigValueType.DECIMAL)
                text.append("\n").append(Component.translatable("screen.brnquest.defaults.source.reset", values.fallback(property)));
        }
        // Registered policy descriptions explain live choices, including creation of a live client-following mode.
        if (!property.field().helpText().isBlank()) {
            if (!text.getString().isBlank()) text.append("\n");
            text.append(Component.translatable(property.field().helpText()));
        }
        return Tooltip.create(text);
    }
    private void validate() {
        if (done == null) return;
        try { values.confirmed(); done.active = true; }
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
            graphics.drawCenteredString(font, Component.translatable(scope == EditorPropertyValues.Scope.CHAPTER
                            ? "screen.brnquest.defaults.chapter_scope_help" : template
                                ? "screen.brnquest.defaults.creation_help" : "screen.brnquest.defaults.instance_help"),
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
                    EditorPropertyPanel.section(font, "screen.brnquest.editor.section." + sections.get(group))
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
            if (!done.active) graphics.drawCenteredString(font, Component.translatable(template ? "screen.brnquest.defaults.invalid" : "screen.brnquest.properties.invalid"),
                    width / 2, panel.bottom() - 45, 0xFFFF8080);
            super.render(graphics, x, y, partial);
            enumDropdown.render(graphics, font, width, viewport.top(), viewport.bottom(), x, y);
            graphics.flush();
        } finally { graphics.pose().popPose(); }
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        UiRect viewport = viewport();
        if (enumDropdown.open()) {
            enumDropdown.click(x, y, button, width, viewport.top(), viewport.bottom());
            closeChoice();
            return true;
        }
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
        if (enumDropdown.open()) closeChoice();
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

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == 256 && enumDropdown.open()) { closeChoice(); return true; }
        return super.keyPressed(key, scanCode, modifiers);
    }

    private UiRect panel() {
        int w = Math.min(680, width - 24), h = Math.min(template ? 500 : contentHeight() + 90, height - 24);
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
