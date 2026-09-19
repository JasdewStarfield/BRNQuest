package yourscraft.jasdewstarfield.brnquest.client.ui;

import yourscraft.jasdewstarfield.brnquest.client.ui.component.GraystonePalette;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.api.RewardView;
import yourscraft.jasdewstarfield.brnquest.editor.*;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypeRegistry;
import yourscraft.jasdewstarfield.brnquest.data.StringMapConfigCodec;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.*;
import java.util.*;
import java.util.function.Consumer;

/** Scrollable typed property form. Draft values survive selectors, scrolling and window resize. */
final class RewardLeafEditorScreen extends RewardEditorScreen {
    private final ResourceLocation type;
    private final Consumer<Map<String, String>> commit;
    private final QuestTypedPropertyFormModel form;
    private final EditorListPanel<Integer> list = new EditorListPanel<>();
    private final java.util.function.UnaryOperator<Map<String, String>> validate;
    private List<EditorPopupMenu.Entry> choices = List.of();
    private EditorPopupMenu.CascadeLayout choiceLayout;
    private int choiceField;

    RewardLeafEditorScreen(Screen parent, ResourceLocation type, Map<String, String> values, Consumer<Map<String, String>> commit) {
        this(parent, ClientRewardPresentationRegistry.get(type).typeName(new RewardView(type, type, type, values, "manual", false)),
                ConfigEditorSchemas.forReward(new RewardView(type, type, type, values, "manual", false)), config -> {
                    var registered = RewardTypeRegistry.get(type);
                    // Client validation is advisory; the server repeats normalization with its own lookup.
                    var level = net.minecraft.client.Minecraft.getInstance().level;
                    var context = level == null ? ConfigNormalizationContext.withoutRegistries()
                            : ConfigNormalizationContext.withRegistries(level.registryAccess());
                    var normalized = new java.util.TreeMap<>(config);
                    normalized.putAll(registered.normalizeConfig(context, config));
                    StringMapConfigCodec.decode(registered.configCodec(), normalized).getOrThrow();
                    return normalized;
                }, commit);
    }

    /** Table settings reuse the same local form without pretending to be an executable reward type. */
    RewardLeafEditorScreen(Screen parent, Component title, ConfigEditorSchema schema,
                           java.util.function.UnaryOperator<Map<String, String>> validate, Consumer<Map<String, String>> commit) {
        super(parent, title);
        this.type = schema.typeId();
        this.commit = commit;
        this.validate = validate;
        form = new QuestTypedPropertyFormModel(Math.max(1, schema.fields().size()));
        form.openExisting(schema, "leaf", "manual");
    }

    @Override protected void init() {
        super.init();
        list.invalidate();
        form.bind(font, this::addRenderableWidget);
        form.hide();
        choiceLayout = null;
    }

    @Override protected UiRect body() {
        var bounds = super.body();
        return new UiRect(bounds.left(), bounds.top() + 14, bounds.right(), bounds.bottom());
    }

    @Override public void render(GuiGraphics graphics, int x, int y, float partial) {
        renderPanel(graphics, x, y, partial);
        var bounds = body();
        // A leaf stays visibly attached to its owning table while its edits are still local.
        if (parent instanceof RewardTableEditorScreen table)
            graphics.drawString(font, font.plainSubstrByWidth(table.breadcrumb() + " / " + title.getString(), bounds.width()),
                    bounds.left(), bounds.top() - 13, GraystonePalette.SECONDARY, false);
        var fields = form.schema().fields();
        var buttons = new ArrayList<>(footer(true, this::apply));
        var issues = form.localIssues();
        var help = new ArrayList<Component>();
        form.hide();
        // Display order is independent of schema indices, preserving extension fields and unknown config.
        var displayRows = new ArrayList<Integer>();
        if (form.schema().rawFallback()) displayRows.add(0);
        else {
            for (boolean required : List.of(true, false)) {
                var indices = java.util.stream.IntStream.range(0, fields.size())
                        .filter(i -> fields.get(i).required() == required).boxed().toList();
                if (!indices.isEmpty()) { displayRows.add(required ? -1 : -2); displayRows.addAll(indices); }
            }
        }
        list.advance(bounds, bounds, bounds.right() + 3, 28, 2,
                displayRows.size(), displayRows::get, frameSeconds(), scrollSpeed());
        list.render(graphics, row -> {
            int index = row.key();
            if (form.schema().rawFallback()) {
                buttons.add(new EditorActionGroup.Placed<>(button("raw", Component.translatable("screen.brnquest.config.edit"),
                        row.bounds(), true, EditorButton.Tone.NEUTRAL, () -> minecraft.setScreen(
                                new EditorRawConfigScreen(this, form.rawConfig(), form::replaceRawConfig))).action(), row.bounds(), row.visible()));
                return;
            }
            if (index < 0) {
                EditorPropertyPanel.section(font, "screen.brnquest.editor.section." + (index == -1 ? "required" : "optional"))
                        .render(graphics, row.bounds().left(), row.bounds().top(), row.bounds().width());
                return;
            }
            var field = fields.get(index);
            var layout = EditorPropertyFormLayout.row(row.bounds().left(), row.bounds().top() + 3,
                    row.bounds().width(), Math.min(150, row.bounds().width() / 3));
            Component label = field.labelKey().isBlank() ? Component.literal(field.key()) : Component.translatable(field.labelKey());
            EditorPropertyRow.label(graphics, font, label, layout.label(), issues.get(field.key()));
            if (row.visible().containsExclusive(x, y) && layout.label().containsExclusive(x, y)) {
                help.add(label);
                if (!field.helpText().isBlank()) help.add(Component.translatable(field.helpText()));
                if (issues.containsKey(field.key())) help.add(Component.literal(issues.get(field.key())));
            }
            if (selector(field)) {
                String value = form.configValue(index);
                Component text = field.valueType() == ConfigValueType.ITEM_STACK
                        ? Component.translatable("screen.brnquest.editor.item_selector.title")
                        : valueLabel(field, value);
                var action = button("field_" + index, text, layout.field(), true, EditorButton.Tone.NEUTRAL, () -> select(index));
                buttons.add(new EditorActionGroup.Placed<>(action.action(), layout.field(), row.visible()));
            } else form.configField(index).show(layout.field(), true);
        }, () -> {});
        // Text widgets share the list clip, so scrolling never draws or activates a field over the footer.
        graphics.enableScissor(bounds.left(), bounds.top(), bounds.right(), bounds.bottom());
        try { super.render(graphics, x, y, partial); }
        finally { graphics.disableScissor(); }
        controls.setActions(buttons);
        controls.render(graphics, font, x, y);
        if (choiceLayout != null) EditorPopupMenu.render(graphics, font, choiceLayout, choices, x, y);
        else if (!help.isEmpty()) graphics.renderComponentTooltip(font, help, x, y);
    }

    private Component valueLabel(ConfigFieldDescriptor field, String value) {
        if (field.valueType() == ConfigValueType.BOOLEAN) return Component.translatable(Boolean.parseBoolean(value) ? "options.on" : "options.off");
        return field.valueLabelKeys().containsKey(value) ? Component.translatable(field.valueLabelKeys().get(value))
                : Component.literal(value.isBlank() ? "…" : value);
    }

    private boolean selector(ConfigFieldDescriptor field) {
        return field.serverSource().isPresent() || field.valueType() == ConfigValueType.ITEM_STACK
                || field.valueType() == ConfigValueType.ENUM || field.valueType() == ConfigValueType.BOOLEAN
                || ClientConfigEditors.find(type, field.key()).isPresent();
    }

    private void select(int index) {
        var field = form.schema().fields().get(index);
        var custom = ClientConfigEditors.find(type, field.key());
        if (custom.isPresent()) minecraft.setScreen(custom.orElseThrow().create(this, form.configValue(index), value -> form.setConfigValue(index, value)));
        else if (field.serverSource().isPresent()) minecraft.setScreen(new ServerFieldScreen(this,
                field.serverSource().orElseThrow().toString(), form.configValue(index),
                value -> form.setConfigValue(index, value), form.currentConfig(), false));
        else if (field.valueType() == ConfigValueType.ITEM_STACK) minecraft.setScreen(new EditorItemSelectorScreen(this, stack -> {
            if (minecraft.level != null) form.setConfigValue(index, stack.save(minecraft.level.registryAccess()).toString());
        }));
        else if (field.valueType() == ConfigValueType.BOOLEAN) form.setConfigValue(index, Boolean.toString(!Boolean.parseBoolean(form.configValue(index))));
        else if (!field.allowedValues().isEmpty()) {
            // Enum fields use the same explicit choice menu as other editor properties, never click-to-cycle.
            choices = EditorPopupMenu.menu(builder -> field.allowedValues().forEach(value ->
                    builder.action(value, valueLabel(field, value), false)));
            choiceField = index;
            var root = EditorPopupMenu.layout(body().left(), body().top(), width, panel().top(), panel().bottom(), 160, choices.size());
            choiceLayout = EditorPopupMenu.cascadeLayout(root, choices, -1, width, panel().top(), panel().bottom(), 160);
        }
    }

    private void apply() {
        try {
            if (!form.localIssues().isEmpty()) throw new IllegalArgumentException(form.localIssues().values().iterator().next());
            var config = validate.apply(form.currentConfig());
            commit.accept(config);
            onClose();
        } catch (RuntimeException error) { issue = Objects.toString(error.getMessage(), "Invalid configuration"); }
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (choiceLayout != null) {
            String value = button == 0 ? EditorPopupMenu.actionAt(choiceLayout, choices, x, y) : "";
            choiceLayout = null;
            if (!value.isBlank()) form.setConfigValue(choiceField, value);
            return true;
        }
        if (controls.mouseClicked(x, y, button) || list.mouseClicked(x, y, button)) return true;
        return body().containsExclusive(x, y) && super.mouseClicked(x, y, button);
    }

    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy) { return list.mouseDragged(y,button) || super.mouseDragged(x,y,button,dx,dy); }
    @Override public boolean mouseReleased(double x,double y,int button) { return list.mouseReleased(button) || super.mouseReleased(x,y,button); }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (choiceLayout != null) return true;
        return list.mouseScrolled(x, y, vertical, scrollStep()) || super.mouseScrolled(x, y, horizontal, vertical);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (choiceLayout != null) { if (key == 256) choiceLayout = null; return true; }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean charTyped(char character, int modifiers) {
        return choiceLayout != null || super.charTyped(character, modifiers);
    }
}
