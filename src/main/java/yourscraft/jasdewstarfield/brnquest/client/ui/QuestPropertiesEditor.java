package yourscraft.jasdewstarfield.brnquest.client.ui;

import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorTextLayout;

import yourscraft.jasdewstarfield.brnquest.editor.QuestPropertySchema;

import yourscraft.jasdewstarfield.brnquest.client.ui.component.GraystonePalette;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.client.ClientEditorState;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButton;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButtonInput;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorConfirmDialog;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPropertyFormLayout;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorFormFields;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPropertyRow;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPropertyPanel;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorTextField;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.QuestScreenLayout;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.QuestIconEditorRow;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
import yourscraft.jasdewstarfield.brnquest.data.DisplayOverrides;
import yourscraft.jasdewstarfield.brnquest.data.QuestNotificationPolicy;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBehavior;
import yourscraft.jasdewstarfield.brnquest.data.QuestIconValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import net.minecraft.client.gui.Font;

/** Owns the complete quest property buffer, its controls and immutable submission intent. */
final class QuestPropertiesEditor {
    interface Host {
        QuestBookSnapshot snapshot();
        QuestScreenLayout layout();
        EditorButtonInput buttons();
        void focus(EditorTextField field);
        void openItems(Consumer<ItemStack> result);
        void openTexture(Consumer<String> result);
        void openLocalized(QuestDefinition quest);
        void openRegistered(Component title, Map<String, String> values, List<QuestPropertySchema.Property> schema,
                            Consumer<Map<String, String>> result);
        void dropdown(UiRect anchor, List<String> values, Consumer<String> result, java.util.function.Function<String, Component> labels);
        void rename(boolean open);
        void resetScroll();
        void panel(GuiGraphics graphics, Component heading, int color, List<EditorPropertyPanel.RowContent> rows,
                   Component done, EditorButton.Tone tone, int mouseX, int mouseY);
        void textButton(GuiGraphics graphics, UiRect bounds, Component label, Component tooltip, boolean enabled,
                        EditorButton.Tone tone, int mouseX, int mouseY);
        QuestIconEditorRow.Layout texture(GuiGraphics graphics, int left, int top, int width, EditorTextField input,
                                         boolean texture, int mouseX, int mouseY);
        boolean submit(Submission intent);
    }
    record Submission(ResourceLocation originalId, QuestDefinition replacement, String iconMode, String iconValue,
                      boolean preserveIcon, Map<String, String> config) {
        Submission { config = Map.copyOf(config); }
    }
    private final Host host;
    private Font font;
    private List<Object> baseline = List.of();
    private final EditorFormIdentity identity = new EditorFormIdentity();
    private static final List<String> QUEST_SHAPES = List.of("chamfer", "square", "circle", "diamond");
    QuestPropertiesEditor(Host host) { this.host = host; }
    void bind(Font font, Consumer<EditorTextField> register) {
        this.font = font;
        clearRenderedPropertyGeometry();
        questFields.bind(font, register);
        questFields.field("title").setNativeFormatting(true);
        questFields.field("subtitle").setNativeFormatting(true);
    }
    boolean open() { return questEditorOpen; }
    ResourceLocation target() { return questEditorQuestId; }
    void hide() { questFields.hide(); }
    void offset(int offset) { questFields.offsetForDrawerAnimation(offset); }
    boolean changed() { return questEditorOpen && !baseline.equals(values()); }
    private List<Object> values() {
        var values = new ArrayList<Object>();
        for (String key : List.of("id", "title", "subtitle", "description", "icon", "x", "y", "shape", "size", "icon_scale", "min_width"))
            values.add(questFields.field(key).getValue());
        values.add(questEditorIconMode); values.add(questEditorBehavior);
        values.add(Objects.toString(questHideDependencyLines, "default"));
        values.add(questNamePosition); values.add(new TreeMap<>(questRegisteredValues)); values.add(questNotifications);
        return List.copyOf(values);
    }
    boolean keyPressed(int key) {
        if (key != 256 || !open()) return false;
        closeQuestEditor();
        return true;
    }
    private <T> Consumer<T> returning(Consumer<T> result) {
        var token = identity.token();
        return value -> {
            var editor = ClientEditorState.get();
            if (identity.matches(token, editor.sessionId(), questEditorQuestId) && EditorSessionLifecycle.canEdit(editor) && !editor.busy()) result.accept(value);
        };
    }
    private String questEditorOriginalIconItemId = "";
    private IconEditorMode questEditorIconMode = IconEditorMode.ITEM;
    private IconEditorMode questEditorOriginalIconMode = IconEditorMode.ITEM;
    private String questEditorItemIconValue = "";
    private String questEditorTextureIconValue = "";
    private QuestIconEditorRow.Layout questIconRowLayout;
    private UiRect questLocalizedTextEditorBounds;
    private UiRect questShapeDropdownBounds;
    private UiRect questNamePositionBounds;
    private UiRect questNotificationsBounds;
    private QuestNotificationPolicy questNotifications = QuestNotificationPolicy.DEFAULT;
    private DisplayOverrides.NamePosition questNamePosition = DisplayOverrides.NamePosition.INHERIT;
    private Map<String, String> questRegisteredValues = Map.of();
    private UiRect questBehaviorEditorBounds;
    private UiRect questAppearanceEditorBounds;
    private QuestBehavior questEditorBehavior = QuestBehavior.DEFAULT;
    private final EditorFormFields<String> questFields = new EditorFormFields<String>()
            .define("title", "screen.brnquest.editor.quest.title", 256)
            .define("subtitle", "screen.brnquest.editor.quest.subtitle", 256)
            .define("description", "screen.brnquest.editor.quest.description", 32768)
            .define("id", "screen.brnquest.editor.quest.id", 256)
            .define("icon", "screen.brnquest.editor.quest.icon", 256)
            .define("x", "screen.brnquest.editor.quest.position_x", 64)
            .define("y", "screen.brnquest.editor.quest.position_y", 64)
            .define("shape", "screen.brnquest.editor.quest.shape", 32)
            .define("size", "screen.brnquest.editor.quest.size", 32)
            .define("icon_scale", "screen.brnquest.editor.quest.icon_scale", 32)
            .define("min_width", "screen.brnquest.editor.quest.min_width", 32);
    private Boolean questHideDependencyLines;
    private boolean questEditorOpen;
    private ResourceLocation questEditorQuestId;
    private Component questEditorMessage;
    void render(GuiGraphics graphics, QuestDefinition quest, int mouseX, int mouseY) {
        boolean enabled = !ClientEditorState.get().busy();
        List<EditorPropertyPanel.RowContent> rows = new ArrayList<>();
        questFields.hide();
        rows.add(EditorPropertyPanel.section(font, "screen.brnquest.editor.section.identity"));
        questFields.field("title").hide();
        questFields.field("subtitle").hide();
        questFields.field("description").hide();
        questFields.field("shape").hide();
        questNamePositionBounds = null;
        questNotificationsBounds = null;
        questLocalizedTextEditorBounds = null;
        questShapeDropdownBounds = null;
        questBehaviorEditorBounds = null;
        questAppearanceEditorBounds = null;
        questIconRowLayout = null;
        rows.add(EditorPropertyPanel.text(font, questFields.field("id"),
                "screen.brnquest.editor.quest.id", 78, null, enabled));
        rows.add(questActionRow("screen.brnquest.editor.quest.localized_text",
                "screen.brnquest.editor.quest.localized_text_open",
                (g, x, y, w) -> renderQuestLocalizedTextEditorField(g, x, y, w, enabled, mouseX, mouseY)));
        rows.add(EditorPropertyPanel.section(font, "screen.brnquest.editor.section.appearance"));
        rows.add(questActionRow("screen.brnquest.editor.section.appearance", "screen.brnquest.properties.appearance_open", (g, x, y, w) -> {
            var row = EditorPropertyRow.action(font, Component.translatable("screen.brnquest.editor.section.appearance"),
                    Component.translatable("screen.brnquest.properties.appearance_open"), x, y, w, 78);
            EditorPropertyRow.label(g, font, Component.translatable("screen.brnquest.editor.section.appearance"), row.label(), null);
            questAppearanceEditorBounds = row.field();
            host.textButton(g, row.field(), Component.translatable("screen.brnquest.properties.appearance_open"), null,
                    enabled, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        }));
        rows.add((g, x, y, w) -> renderQuestIconEditorField(g, x, y, w, mouseX, mouseY));
        rows.add(this::renderQuestPositionEditorField);
        rows.add(EditorPropertyPanel.section(font, "screen.brnquest.editor.section.quest_rules"));
        rows.add(questActionRow("screen.brnquest.editor.quest.behavior",
                "screen.brnquest.editor.quest.behavior_open",
                (g, x, y, w) -> renderQuestBehaviorEditorField(g, x, y, w, enabled, mouseX, mouseY)));
        rows.add(questActionRow("screen.brnquest.notifications.title", "screen.brnquest.notifications.configure", (g, x, y, w) -> {
            var row = EditorPropertyRow.action(font, Component.translatable("screen.brnquest.notifications.title"),
                    Component.translatable("screen.brnquest.notifications.configure"), x, y, w, 78);
            EditorPropertyRow.label(g, font, Component.translatable("screen.brnquest.notifications.title"), row.label(), null);
            questNotificationsBounds = row.field();
            host.textButton(g, row.field(), Component.translatable("screen.brnquest.notifications.configure"), null,
                    enabled, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        }));
        Component heading = questEditorMessage == null
                ? Component.translatable("screen.brnquest.editor.quest.heading") : questEditorMessage;
        host.panel(graphics, heading, questEditorMessage == null ? 0xFFFFFFFF : 0xFFFF8B8B,
                rows, Component.translatable("gui.done"), EditorButton.Tone.PRIMARY, mouseX, mouseY);
    }
    private void renderQuestLocalizedTextEditorField(GuiGraphics graphics, int left, int top, int width, boolean enabled,
                                                     int mouseX, int mouseY) {
        EditorPropertyFormLayout.Row row = EditorPropertyRow.action(font,
                Component.translatable("screen.brnquest.editor.quest.localized_text"),
                Component.translatable("screen.brnquest.editor.quest.localized_text_open"), left, top, width, 78);
        EditorPropertyRow.label(graphics, font, Component.translatable("screen.brnquest.editor.quest.localized_text"),
                row.label(), null);
        questLocalizedTextEditorBounds = row.field();
        host.textButton(graphics, row.field(),
                Component.translatable("screen.brnquest.editor.quest.localized_text_open"),
                Component.translatable("screen.brnquest.editor.quest.localized_text_hint"), enabled,
                EditorButton.Tone.NEUTRAL, mouseX, mouseY);
    }
    private void renderQuestShapeEditorField(GuiGraphics graphics, int left, int top, int width,
                                             boolean enabled, int mouseX, int mouseY) {
        EditorPropertyFormLayout.Row row = EditorPropertyFormLayout.row(left, top, width, 78);
        EditorPropertyRow.label(graphics, font, Component.translatable("screen.brnquest.editor.quest.shape"),
                row.label(), null);
        questShapeDropdownBounds = row.field();
        host.textButton(graphics, row.field(), authorValueLabel("shape", questFields.field("shape").getValue()), null, enabled, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
    }
    private void renderQuestBehaviorEditorField(GuiGraphics graphics, int left, int top, int width,
                                                boolean enabled, int mouseX, int mouseY) {
        EditorPropertyFormLayout.Row row = EditorPropertyRow.action(font,
                Component.translatable("screen.brnquest.editor.quest.behavior"),
                Component.translatable("screen.brnquest.editor.quest.behavior_open"), left, top, width, 78);
        EditorPropertyRow.label(graphics, font, Component.translatable("screen.brnquest.editor.quest.behavior"),
                row.label(), null);
        questBehaviorEditorBounds = row.field();
        host.textButton(graphics, row.field(),
                Component.translatable("screen.brnquest.editor.quest.behavior_open"), null,
                enabled, EditorButton.Tone.NEUTRAL, mouseX, mouseY);
    }
    private EditorPropertyPanel.RowContent questActionRow(String labelKey, String actionKey,
                                                         EditorPropertyPanel.RowContent content) {
        return EditorPropertyPanel.sized(w -> {
            var row = EditorPropertyRow.action(font, Component.translatable(labelKey), Component.translatable(actionKey),
                    0, 0, w, 78);
            return Math.max(row.label().bottom(), row.field().bottom());
        }, content);
    }
    private void renderQuestIconEditorField(GuiGraphics graphics, int left, int top, int width,
                                            int mouseX, int mouseY) {
        questIconRowLayout = host.texture(graphics, left, top, width, questFields.field("icon"),
                questEditorIconMode == IconEditorMode.TEXTURE, mouseX, mouseY);
    }
    private void renderQuestPositionEditorField(GuiGraphics graphics, int left, int top, int width) {
        int labelWidth = 48;
        EditorPropertyFormLayout.Row row = EditorPropertyFormLayout.row(left, top, width, labelWidth);
        String label = EditorTextLayout.ellipsize(Component.translatable("screen.brnquest.editor.quest.position").getString(), row.label().width() - 4, font::width, false);
        graphics.drawString(font, Component.literal(label), row.label().left(), row.label().top() + 5,
                GraystonePalette.SECONDARY, false);

        int axisLabelWidth = 10;
        int gap = 4;
        int inputWidth = Math.max(16, (row.field().width() - axisLabelWidth * 2 - gap) / 2);
        int xLabelLeft = row.field().left();
        int xInputLeft = xLabelLeft + axisLabelWidth;
        int yLabelLeft = xInputLeft + inputWidth + gap;
        int yInputLeft = yLabelLeft + axisLabelWidth;
        graphics.drawString(font, "X", xLabelLeft + 1, row.field().top() + 5, GraystonePalette.SECONDARY, false);
        graphics.drawString(font, "Y", yLabelLeft + 1, row.field().top() + 5, GraystonePalette.SECONDARY, false);
        questFields.field("x").show(new UiRect(xInputLeft, row.field().top(), xInputLeft + inputWidth, row.field().bottom()),
                !ClientEditorState.get().busy());
        questFields.field("y").show(new UiRect(yInputLeft, row.field().top(), row.field().right(), row.field().bottom()),
                !ClientEditorState.get().busy());
    }
    private void openQuestIconItemSelector() {
        host.openItems(returning(stack -> {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (itemId == null) return;
            String value = itemId.toString();
            questFields.field("icon").setValue(value);
            questEditorItemIconValue = value;
            questEditorMessage = null;
        }));
    }
    void open(QuestDefinition quest) {
        if (ClientEditorState.get().busy() || !EditorSessionLifecycle.canEdit(ClientEditorState.get())) return;
        identity.open(ClientEditorState.get().sessionId(), quest.id());
        host.resetScroll();
        questEditorOpen = true;
        questEditorQuestId = quest.id();
        questEditorMessage = null;
        questFields.field("id").setValue(quest.id().toString());
        questFields.field("title").setValue(quest.title());
        questFields.field("subtitle").setValue(quest.subtitle());
        questFields.field("description").setValue(quest.description());
        questEditorIconMode = QuestIconValue.isTexture(quest.icon()) ? IconEditorMode.TEXTURE : IconEditorMode.ITEM;
        questEditorOriginalIconMode = questEditorIconMode;
        questEditorOriginalIconItemId = questEditorIconMode == IconEditorMode.TEXTURE
                ? QuestIconValue.textureId(quest.icon()).map(ResourceLocation::toString).orElse("")
                : iconItemId(quest.icon());
        questEditorItemIconValue = questEditorIconMode == IconEditorMode.ITEM ? questEditorOriginalIconItemId : "";
        questEditorTextureIconValue = questEditorIconMode == IconEditorMode.TEXTURE
                ? questEditorOriginalIconItemId : "";
        questFields.field("icon").setValue(questEditorOriginalIconItemId);
        questFields.field("x").setValue(Double.toString(quest.x()));
        questFields.field("y").setValue(Double.toString(quest.y()));
        questFields.field("shape").setValue(quest.appearance().shape());
        questFields.field("size").setValue(Double.toString(quest.appearance().size()));
        questFields.field("icon_scale").setValue(Double.toString(quest.appearance().iconScale()));
        questFields.field("min_width").setValue(Double.toString(quest.appearance().minWidth()));
        questEditorBehavior = quest.behavior();
        questHideDependencyLines = quest.appearance().hideDependencyLines();
        questNamePosition = DisplayOverrides.name(quest.extensions());
        questNotifications = QuestNotificationPolicy.read(quest.extensions());
        questRegisteredValues = QuestPropertySchema.values(quest.appearance(), quest.behavior(), quest.extensions());
        baseline = values();
        host.focus(questFields.field("id"));
    }
    private void prepareQuestPropertyEdit() {
        ResourceLocation replacementId = ResourceLocation.tryParse(questFields.field("id").getValue());
        if (replacementId == null) {
            questEditorMessage = Component.translatable("screen.brnquest.editor.quest.invalid_id");
            return;
        }
        String iconItemId = questFields.field("icon").getValue().strip();
        ResourceLocation parsedIcon = iconItemId.isEmpty() ? null : ResourceLocation.tryParse(iconItemId);
        if (!iconItemId.isEmpty() && (parsedIcon == null || (questEditorIconMode == IconEditorMode.ITEM
                && !BuiltInRegistries.ITEM.containsKey(parsedIcon)))) {
            questEditorMessage = Component.translatable(questEditorIconMode == IconEditorMode.ITEM
                    ? "screen.brnquest.editor.quest.invalid_icon" : "screen.brnquest.editor.quest.invalid_texture");
            return;
        }
        Double x = parseFiniteCoordinate(questFields.field("x"));
        Double y = parseFiniteCoordinate(questFields.field("y"));
        Double size = parsePositive(questFields.field("size"), false);
        Double iconScale = parsePositive(questFields.field("icon_scale"), false);
        Double minWidth = parsePositive(questFields.field("min_width"), true);
        if (x == null || y == null || size == null || iconScale == null || minWidth == null
                || questFields.field("shape").getValue().isBlank()) {
            questEditorMessage = Component.translatable("screen.brnquest.editor.quest.invalid_position");
            return;
        }
        QuestDefinition currentQuest = draftQuest(questEditorQuestId);
        if (!replacementId.equals(questEditorQuestId) && currentQuest != null
                && (Double.compare(x, currentQuest.x()) != 0 || Double.compare(y, currentQuest.y()) != 0)) {
            // A rename migrates aliases and a coordinate update changes content; keeping them as separate
            // revision steps prevents a confirmed rename from racing a second mutation request.
            questEditorMessage = Component.translatable("screen.brnquest.editor.quest.rename_position_conflict");
            return;
        }
        questEditorMessage = null;
        if (!replacementId.equals(questEditorQuestId)) {
            host.rename(true);
            return;
        }
        submitQuestPropertyEdit(replacementId);
    }
    private static Double parseFiniteCoordinate(EditorTextField field) {
        if (field == null) return null;
        try {
            double value = Double.parseDouble(field.getValue().strip());
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
    private static Double parsePositive(EditorTextField field, boolean allowZero) {
        Double value = parseFiniteCoordinate(field);
        return value != null && (allowZero ? value >= 0 : value > 0) ? value : null;
    }
    private QuestDefinition draftQuest(ResourceLocation questId) {
        QuestBookSnapshot snapshot = host.snapshot();
        return snapshot == null || questId == null ? null : snapshot.quests().get(questId);
    }
    void closeQuestEditor() {
        identity.close();
        questEditorOpen = false;
        questEditorQuestId = null;
        questEditorMessage = null;
        questEditorOriginalIconItemId = "";
        questEditorIconMode = IconEditorMode.ITEM;
        questEditorOriginalIconMode = IconEditorMode.ITEM;
        questEditorItemIconValue = "";
        questEditorTextureIconValue = "";
        questEditorBehavior = QuestBehavior.DEFAULT;
        clearRenderedPropertyGeometry();
        host.focus(null);
        for (EditorTextField field : List.of(questFields.field("id"), questFields.field("title"), questFields.field("subtitle"),
                questFields.field("description"), questFields.field("icon"), questFields.field("x"), questFields.field("y"),
                questFields.field("shape"), questFields.field("size"), questFields.field("icon_scale"),
                questFields.field("min_width"))) {
            if (field == null) continue;
            field.hide();
        }
    }
    private void clearRenderedPropertyGeometry() {
        questAppearanceEditorBounds = null;
        questIconRowLayout = null;
        questLocalizedTextEditorBounds = null;
        questShapeDropdownBounds = null;
        questNamePositionBounds = null;
        questNotificationsBounds = null;
        questBehaviorEditorBounds = null;
    }
    private Map<String, String> behaviorConfig(QuestBehavior value) { return registeredQuestValues(value); }
    private Map<String, String> registeredQuestValues() { return registeredQuestValues(questEditorBehavior); }
    private Map<String, String> registeredQuestValues(QuestBehavior behavior) {
        var staged = new TreeMap<>(questRegisteredValues);
        staged.put("shape", questFields.field("shape").getValue());
        staged.put("size", questFields.field("size").getValue());
        staged.put("icon_scale", questFields.field("icon_scale").getValue());
        staged.put("min_width", questFields.field("min_width").getValue());
        staged.put("hide_dependency_lines", java.util.Objects.toString(questHideDependencyLines, "default"));
        var appearance = QuestPropertySchema.appearance(staged);
        var extensions = QuestPropertySchema.CATALOG.extensions(Map.of(), questRegisteredValues);
        var changes = new TreeMap<>(questNotifications.fields());
        changes.put("quest_name_position", questNamePosition.name().toLowerCase(Locale.ROOT));
        return QuestPropertySchema.values(appearance, behavior, QuestPropertySchema.CATALOG.extensions(extensions, changes));
    }
    private void applyRegisteredQuestValues(Map<String, String> changes) {
        var values = new TreeMap<>(registeredQuestValues()); values.putAll(changes);
        var appearance = QuestPropertySchema.appearance(values);
        questFields.field("shape").setValue(appearance.shape());
        questFields.field("size").setValue(Double.toString(appearance.size()));
        questFields.field("icon_scale").setValue(Double.toString(appearance.iconScale()));
        questFields.field("min_width").setValue(Double.toString(appearance.minWidth()));
        questHideDependencyLines = appearance.hideDependencyLines();
        questEditorBehavior = QuestPropertySchema.behavior(values);
        var extensions = QuestPropertySchema.CATALOG.extensions(Map.of(), values);
        questNamePosition = DisplayOverrides.name(extensions);
        questNotifications = QuestNotificationPolicy.read(extensions);
        questRegisteredValues = Map.copyOf(values);
    }
    private static String iconItemId(String iconSnbt) {
        if (iconSnbt == null || iconSnbt.isBlank()) return "";
        try {
            return TagParser.parseTag(iconSnbt).getString("id");
        } catch (Exception ignored) {
            // Unknown legacy icon data stays untouched until the author selects a replacement item.
            return "";
        }
    }
    private void rememberCurrentIconInput() {
        if (questEditorIconMode == IconEditorMode.ITEM) {
            questEditorItemIconValue = questFields.field("icon").getValue();
        } else {
            questEditorTextureIconValue = questFields.field("icon").getValue();
        }
    }
    void renderRename(GuiGraphics graphics, int mouseX, int mouseY) {
        long references = host.snapshot() == null || questEditorQuestId == null ? 0L
                : host.snapshot().book().quests().stream()
                .filter(quest -> quest.dependencies().contains(questEditorQuestId)).count();
        EditorConfirmDialog.render(graphics, font, host.layout(), host.buttons(),
                Component.translatable("screen.brnquest.editor.quest.rename.warning"),
                Component.translatable("screen.brnquest.editor.quest.rename.impact", references), 0xFFFFC06A,
                Component.translatable("gui.cancel"),
                Component.translatable("screen.brnquest.editor.quest.rename.confirm"), mouseX, mouseY);
    }
    boolean clickRename(double mouseX, double mouseY, int button) {
        if (button != 0) return true;
        EditorConfirmDialog.Action action = EditorConfirmDialog.actionAt(host.layout(), mouseX, mouseY);
        if (action == EditorConfirmDialog.Action.CANCEL) {
            host.rename(false);
            return true;
        }
        if (action == EditorConfirmDialog.Action.CONFIRM) {
            ResourceLocation replacementId = ResourceLocation.tryParse(questFields.field("id").getValue());
            if (replacementId != null) submitQuestPropertyEdit(replacementId);
        }
        return true;
    }
    boolean click(double mouseX, double mouseY, int button, UiRect viewport, UiRect save, UiRect cancel) {
        if (!open() || !EditorSessionLifecycle.canEdit(ClientEditorState.get())) return true;
        if (ClientEditorState.get().busy()) return true;
            // Partially visible controls may only receive clicks in the clipped part; footer stays independent.
            if (!viewport.containsExclusive(mouseX, mouseY)
                    && !save.contains(mouseX, mouseY)
                    && !cancel.contains(mouseX, mouseY)) return true;
            if (button == 0 && questLocalizedTextEditorBounds != null
                    && questLocalizedTextEditorBounds.contains(mouseX, mouseY)) {
                QuestDefinition quest = draftQuest(questEditorQuestId);
                if (quest != null) host.openLocalized(quest);
                return true;
            }
            if (button == 0 && questShapeDropdownBounds != null
                    && questShapeDropdownBounds.contains(mouseX, mouseY)) {
                host.dropdown(questShapeDropdownBounds, QUEST_SHAPES,
                        questFields.field("shape")::setValue, value -> authorValueLabel("shape", value));
                return true;
            }
            if (button == 0 && questNamePositionBounds != null && questNamePositionBounds.contains(mouseX, mouseY)) {
                host.dropdown(questNamePositionBounds, List.of("inherit", "off", "above", "below"),
                        value -> questNamePosition = DisplayOverrides.parseName(value),
                        value -> Component.translatable("screen.brnquest.display.name." + value));
                return true;
            }
            if (button == 0 && questAppearanceEditorBounds != null && questAppearanceEditorBounds.contains(mouseX, mouseY)) {
                host.openRegistered( Component.translatable("screen.brnquest.editor.section.appearance"),
                        registeredQuestValues(), QuestPropertySchema.instance("appearance"), returning(this::applyRegisteredQuestValues));
                return true;
            }
            if (button == 0 && questNotificationsBounds != null && questNotificationsBounds.contains(mouseX, mouseY)) {
                host.openRegistered( Component.translatable("screen.brnquest.notifications.title"),
                        registeredQuestValues(), QuestPropertySchema.instance("notifications"), returning(this::applyRegisteredQuestValues));
                return true;
            }
            if (button == 0 && questBehaviorEditorBounds != null
                    && questBehaviorEditorBounds.contains(mouseX, mouseY)) {
                host.openRegistered( Component.translatable("screen.brnquest.editor.behavior.title"),
                        registeredQuestValues(), QuestPropertySchema.CATALOG.properties().stream()
                                .filter(property -> Set.of("visibility", "dependencies", "completion").contains(property.section())).toList(),
                        returning(this::applyRegisteredQuestValues));
                return true;
            }
            if (button == 0 && questIconRowLayout != null && questIconRowLayout.mode().contains(mouseX, mouseY)) {
                rememberCurrentIconInput();
                questEditorIconMode = questEditorIconMode == IconEditorMode.ITEM
                        ? IconEditorMode.TEXTURE : IconEditorMode.ITEM;
                questFields.field("icon").setValue(questEditorIconMode == IconEditorMode.ITEM
                        ? questEditorItemIconValue : questEditorTextureIconValue);
                host.focus(questFields.field("icon"));
                return true;
            }
            if (button == 0 && questEditorIconMode == IconEditorMode.TEXTURE
                    && questIconRowLayout != null && questIconRowLayout.picker().contains(mouseX, mouseY)
                    && !ClientEditorState.get().busy()) {
                host.openTexture(returning(questFields.field("icon")::setValue));
                return true;
            }
            if (button == 0 && questEditorIconMode == IconEditorMode.ITEM
                    && questIconRowLayout != null && questIconRowLayout.picker().contains(mouseX, mouseY)
                    && !ClientEditorState.get().busy()) {
                openQuestIconItemSelector();
                return true;
            }
            if (button == 0 && save.contains(mouseX, mouseY)) {
                prepareQuestPropertyEdit();
                return true;
            }
            if (button == 0 && cancel.contains(mouseX, mouseY)) {
                closeQuestEditor();
                return true;
            }
        return false;
    }
    private static Component authorValueLabel(String category, String value) {
        String key = "screen.brnquest.editor.value." + category + "." + value;
        return net.minecraft.client.resources.language.I18n.exists(key) ? Component.translatable(key) : Component.literal(value);
    }
    private void submitQuestPropertyEdit(ResourceLocation replacementId) {
        var editor = ClientEditorState.get();
        if (!open() || editor.busy() || !EditorSessionLifecycle.canEdit(editor)) return;
        QuestDefinition currentQuest = draftQuest(questEditorQuestId);
        if (currentQuest == null) return;
        String iconValue = questFields.field("icon").getValue().strip();
        boolean preserveIcon = questEditorIconMode == questEditorOriginalIconMode && iconValue.equals(questEditorOriginalIconItemId);
        String icon = preserveIcon ? currentQuest.icon() : iconValue.isBlank() ? ""
                : questEditorIconMode == IconEditorMode.TEXTURE ? QuestIconValue.texture(ResourceLocation.parse(iconValue))
                : "{id:\"" + iconValue + "\",count:1}";
        var replacement = new QuestDefinition(currentQuest.bookId(), replacementId, currentQuest.chapterId(),
                currentQuest.title(), currentQuest.subtitle(), currentQuest.description(), currentQuest.descriptionFormat(), icon,
                Double.parseDouble(questFields.field("x").getValue().strip()), Double.parseDouble(questFields.field("y").getValue().strip()),
                currentQuest.dependencies(), currentQuest.tasks(), currentQuest.rewards(), currentQuest.legacyId(),
                QuestPropertySchema.appearance(registeredQuestValues()), questEditorBehavior,
                QuestPropertySchema.CATALOG.extensions(currentQuest.extensions(), registeredQuestValues()));
        if (host.submit(new Submission(questEditorQuestId, replacement, questEditorIconMode.name(), iconValue,
                preserveIcon, registeredQuestValues()))) closeQuestEditor();
    }
    private enum IconEditorMode { ITEM, TEXTURE }
}
