package yourscraft.jasdewstarfield.brnquest.client.ui;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.GraystonePalette;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import yourscraft.jasdewstarfield.brnquest.client.ClientEditorState;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorButton;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorIcon;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPublishReviewModel;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPropertyFormLayout;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPropertyRow;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorPropertyPanel;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorTextField;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.QuestActionIcons;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.QuestScreenLayout;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
import yourscraft.jasdewstarfield.brnquest.api.ApiViews;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.data.RewardClaimPolicy;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigEditorSchema;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigEditorSchemas;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigFieldDescriptor;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigValueType;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypeRegistry;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypeRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.Minecraft;

/**
 * Coordinates one typed task/reward property form without owning protocol dispatch.
 * The parent Screen converts a validated immutable submission into the existing server mutation.
 */
final class QuestTypedPropertySection {
    /** Rendering and child-screen services, without access to parent form state. */
    interface Host {
        EditorSessionView session();
        QuestTypedEntryKind kind();
        ResourceLocation quest();
        QuestBookSnapshot snapshot();
        QuestScreenFrameIdentity frame();
        QuestScreenLayout layout();
        EditorChildScreens children();
        void focus(EditorTextField field);
        void resetScroll();
        UiRect viewport();
        UiRect cancel();
        UiRect save();
        UiRect row(int index);
        UiRect visible(UiRect bounds);
        void panel(GuiGraphics g, Component heading, int color, List<EditorPropertyPanel.RowContent> rows, Component done, EditorButton.Tone tone, int x, int y);
        void action(GuiGraphics g, UiRect bounds, EditorButton.Definition definition, boolean enabled, EditorButton.Tone tone, int x, int y);
        void text(GuiGraphics g, UiRect bounds, Component label, Component tooltip, boolean enabled, EditorButton.Tone tone, int x, int y);
        void dropdownButton(GuiGraphics g, UiRect bounds, Component label, boolean enabled, int x, int y);
        void dropdown(UiRect anchor, List<String> values, Consumer<String> result, java.util.function.Function<String, Component> labels);
        ItemStack item(ResourceLocation id, String snbt);
        void invalidateIcon(ResourceLocation id);
        void tooltip(List<Component> text);
        List<EditorPublishReviewModel.Diagnostic> diagnostics();
        void currentRequest(String id, String source, String currentValue);
        boolean submit(String action, ResourceLocation quest, Submission submission);
    }
    private Host host;
    private Font font;
    private List<Object> baseline = List.of();
    private final EditorFormIdentity identity = new EditorFormIdentity();
    private static final int MAX_TYPED_CONFIG_FIELDS = 8;
    private static final List<String> REWARD_CLAIM_POLICIES = java.util.Arrays.stream(RewardClaimPolicy.values())
            .map(RewardClaimPolicy::serializedName).toList();
    void attach(Host host) { this.host = host; }
    boolean changed() { return open && !baseline.equals(values()); }
    private List<Object> values() { return List.of(form.id(), form.claim(), form.currentConfig(), optional, teamReward); }
    private <T> Consumer<T> returning(Consumer<T> result) {
        var token = identity.token();
        return value -> {
            var editor = host.session();
            if (identity.matches(token, editor.sessionId(), originalId) && editor.editable() && !editor.busy()) result.accept(value);
        };
    }
    private final Map<Integer, UiRect> typedConfigRenderedFields = new java.util.HashMap<>();
    private Component typedPropertyMessage;

    enum PreparationStatus { INVALID_ID, LOCAL_ISSUE, CONFIRM_RENAME, CLAIM_REQUIRED, READY }
    enum Action { CANCEL, SUBMIT, CLAIM, SERVER_CURRENT, SERVER_FIELD, CUSTOM, BOOLEAN, ENUM, ITEM, MATCHER, RAW, OPTIONAL, TEAM_REWARD }

    record FieldHit(int index, ConfigFieldDescriptor descriptor, UiRect bounds) {}

    record InteractionFrame(QuestScreenFrameIdentity identity, QuestTypedEntryKind kind,
                            UiRect cancelBounds, UiRect submitBounds, UiRect claimBounds,
                            List<FieldHit> fields, UiRect rawBounds, UiRect optionalBounds,
                            UiRect teamRewardBounds) {
        InteractionFrame {
            fields = List.copyOf(fields);
        }
    }

    record Intent(Action action, int fieldIndex, UiRect anchor, List<String> values) {
        Intent {
            values = List.copyOf(values);
        }

        static Intent simple(Action action, UiRect anchor) {
            return new Intent(action, -1, anchor, List.of());
        }
    }

    record Submission(ResourceLocation replacementId, ResourceLocation sourceId, String claimPolicy,
                      int semanticFlag, Map<String, String> config) {
        Submission {
            config = Map.copyOf(config);
        }
    }

    record Preparation(PreparationStatus status, String fieldKey, String issue, Submission submission) {
        static Preparation problem(PreparationStatus status, String fieldKey, String issue) {
            return new Preparation(status, fieldKey, issue, null);
        }

        static Preparation ready(Submission submission) {
            return new Preparation(PreparationStatus.READY, "", "", submission);
        }
    }

    private final QuestTypedPropertyFormModel form;
    private boolean open;
    private ResourceLocation originalId;
    private ResourceLocation typeId;
    private boolean creating;
    private boolean optional;
    private boolean teamReward;
    private boolean renameArmed;
    private boolean submissionPending;
    private InteractionFrame interactionFrame;
    private record PendingCurrent(String id, int index, String previous) {}
    private PendingCurrent pendingCurrent;

    QuestTypedPropertySection(int fieldCapacity) {
        form = new QuestTypedPropertyFormModel(fieldCapacity);
    }

    void bind(Font font, Consumer<EditorTextField> register) { this.font = font; form.bind(font, register); }
    void hide() { form.hide(); }
    void offsetForDrawerAnimation(int offset) { form.offsetForDrawerAnimation(offset); }
    QuestTypedPropertyFormModel form() { return form; }
    boolean open() { return open; }
    ResourceLocation originalId() { return originalId; }
    ResourceLocation typeId() { return typeId; }
    boolean creating() { return creating; }
    boolean optional() { return optional; }
    boolean teamReward() { return teamReward; }
    boolean renameArmed() { return renameArmed; }
    boolean submissionPending() { return submissionPending; }

    void openExisting(QuestTypedEntryKind.Entry entry, ConfigEditorSchema schema) {
        open = true;
        originalId = entry.id();
        identity.open(host == null ? null : host.session().sessionId(), originalId);
        typeId = entry.typeId();
        creating = false;
        optional = entry.optional();
        teamReward = entry.teamReward();
        renameArmed = false;
        submissionPending = false;
        interactionFrame = null;
        pendingCurrent = null;
        form.openExisting(schema, entry.id().toString(), entry.claimPolicy());
    }

    void openNew(ResourceLocation id, ResourceLocation nextTypeId, ConfigEditorSchema schema) {
        open = true;
        originalId = id;
        identity.open(host == null ? null : host.session().sessionId(), originalId);
        typeId = nextTypeId;
        creating = true;
        optional = false;
        teamReward = false;
        renameArmed = false;
        submissionPending = false;
        interactionFrame = null;
        pendingCurrent = null;
        form.openNew(schema, id.toString(), "manual");
    }

    /** Seed reward semantics without changing whether this form creates or updates an entry. */
    void creationRewardDefaults(String policy, boolean team) {
        teamReward = team;
        form.openNew(form.schema(), originalId.toString(), policy);
    }

    void toggleOptional() { optional = !optional; }
    void toggleTeamReward() { teamReward = !teamReward; }
    void markSubmissionPending() { submissionPending = true; pendingCurrent = null; }
    void clearSubmissionPending() { submissionPending = false; }

    void captureInteractionFrame(InteractionFrame frame) { interactionFrame = frame; }

    /** Accept input only against geometry produced by the current book/revision/mode frame. */
    Optional<Intent> click(QuestScreenFrameIdentity identity, QuestTypedEntryKind kind, double x, double y) {
        InteractionFrame frame = interactionFrame;
        if (!open || frame == null || identity == null || !frame.identity().equals(identity)
                || frame.kind() != kind) return Optional.empty();
        if (frame.cancelBounds().contains(x, y)) return Optional.of(Intent.simple(Action.CANCEL, frame.cancelBounds()));
        if (frame.submitBounds().contains(x, y)) return Optional.of(Intent.simple(Action.SUBMIT, frame.submitBounds()));
        if (kind == QuestTypedEntryKind.REWARD && frame.claimBounds() != null
                && frame.claimBounds().contains(x, y)) {
            return Optional.of(Intent.simple(Action.CLAIM, frame.claimBounds()));
        }
        for (FieldHit field : frame.fields()) {
            if (!field.bounds().contains(x, y)) continue;
            ConfigValueType type = field.descriptor().valueType();
            if (ClientConfigEditors.find(typeId, field.descriptor().key()).isPresent())
                return Optional.of(new Intent(Action.CUSTOM, field.index(), field.bounds(), List.of()));
            if (type == ConfigValueType.INTEGER_VECTOR3) {
                var row = yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorVectorRow.layout(field.bounds(), field.descriptor().serverSource().isPresent());
                return row.current() != null && row.current().containsExclusive(x, y)
                        ? Optional.of(new Intent(Action.SERVER_CURRENT, field.index(), row.current(), List.of())) : Optional.empty();
            }
            Action action = field.descriptor().serverSource().isPresent() ? Action.SERVER_FIELD : switch (type) {
                case BOOLEAN -> Action.BOOLEAN;
                case ENUM -> Action.ENUM;
                case ITEM_STACK -> Action.ITEM;
                case ITEM_MATCHER -> Action.MATCHER;
                default -> null;
            };
            if (action == null) return Optional.empty();
            return Optional.of(new Intent(action, field.index(), field.bounds(),
                    type == ConfigValueType.ENUM ? field.descriptor().allowedValues() : List.of()));
        }
        if (frame.rawBounds() != null && frame.rawBounds().contains(x, y)) {
            return Optional.of(Intent.simple(Action.RAW, frame.rawBounds()));
        }
        if (kind == QuestTypedEntryKind.TASK && frame.optionalBounds() != null
                && frame.optionalBounds().contains(x, y)) {
            return Optional.of(Intent.simple(Action.OPTIONAL, frame.optionalBounds()));
        }
        if (kind == QuestTypedEntryKind.REWARD && frame.teamRewardBounds() != null
                && frame.teamRewardBounds().contains(x, y)) {
            return Optional.of(Intent.simple(Action.TEAM_REWARD, frame.teamRewardBounds()));
        }
        return Optional.empty();
    }

    Preparation prepare(QuestTypedEntryKind kind, Map<String, String> localIssues) {
        ResourceLocation replacementId = ResourceLocation.tryParse(form.id().strip());
        if (replacementId == null) {
            return Preparation.problem(PreparationStatus.INVALID_ID, "id", "");
        }
        if (!localIssues.isEmpty()) {
            Map.Entry<String, String> issue = localIssues.entrySet().iterator().next();
            return Preparation.problem(PreparationStatus.LOCAL_ISSUE, issue.getKey(), issue.getValue());
        }
        if (!creating && !replacementId.equals(originalId) && !renameArmed) {
            renameArmed = true;
            return Preparation.problem(PreparationStatus.CONFIRM_RENAME, "id", "");
        }
        if (kind == QuestTypedEntryKind.REWARD && form.claim().isBlank()) {
            return Preparation.problem(PreparationStatus.CLAIM_REQUIRED, "claim_policy", "");
        }
        ResourceLocation source = creating ? typeId : originalId;
        String claim = kind == QuestTypedEntryKind.REWARD ? form.claim().strip() : "";
        int semantics = kind == QuestTypedEntryKind.TASK ? (optional ? 1 : 0) : (teamReward ? 1 : 0);
        return Preparation.ready(new Submission(replacementId, source, claim, semantics, form.currentConfig()));
    }

    /** Correlates direct-current responses and prevents late replies from overwriting further typing. */
    String beginCurrentRequest(int index) {
        String id = java.util.UUID.randomUUID().toString();
        pendingCurrent = new PendingCurrent(id, index, form.configValue(index));
        return id;
    }
    void receiveCurrent(String id, String value) {
        var pending = pendingCurrent;
        if (!open || pending == null || !pending.id().equals(id)) return;
        pendingCurrent = null;
        if (!value.isBlank() && form.configValue(pending.index()).equals(pending.previous())) form.setConfigValue(pending.index(), value);
    }

    void close() {
        identity.close();
        typedPropertyMessage = null;
        open = false;
        originalId = null;
        typeId = null;
        creating = false;
        optional = false;
        teamReward = false;
        renameArmed = false;
        submissionPending = false;
        interactionFrame = null;
        pendingCurrent = null;
        form.close();
    }

    void openEditor(QuestDefinition quest, ResourceLocation typedId) {
        QuestTypedEntryKind.Entry entry = host.kind().entry(quest, typedId);
        if (entry == null || host.session().busy()) return;
        typedPropertyMessage = null;
        ConfigEditorSchema schema = host.kind() == QuestTypedEntryKind.TASK
                ? ConfigEditorSchemas.forTask(ApiViews.task(entry.task()))
                : ConfigEditorSchemas.forReward(ApiViews.reward(entry.reward()));
        // Item tasks project both historical config shapes into one canonical editor model.
        // The original map for this form must be that projection so hidden legacy keys do
        // not silently survive a successful canonical save.
        host.resetScroll();
        openExisting(entry, schema);
        baseline = values();
        host.focus(form().identityField("id"));
    }

    void openNewEditor(QuestBookSnapshot snapshot, ResourceLocation typeId) {
        ResourceLocation id = suggestId(snapshot.book(), host.kind().idStem());
        typedPropertyMessage = null;
        ConfigEditorSchema schema = host.kind() == QuestTypedEntryKind.TASK
                ? ConfigEditorSchemas.forTask(new yourscraft.jasdewstarfield.brnquest.api.TaskView(
                        snapshot.book().id(), id, typeId, yourscraft.jasdewstarfield.brnquest.author.EntryCreationPolicy.taskConfig(
                                snapshot.book(), host.quest(), typeId, Map.of()), false))
                : ConfigEditorSchemas.forReward(new yourscraft.jasdewstarfield.brnquest.api.RewardView(
                        snapshot.book().id(), id, typeId, Map.of(), "manual", false));
        host.resetScroll();
        openNew(id, typeId, schema);
        if (host.kind() == QuestTypedEntryKind.REWARD) creationRewardDefaults(
                yourscraft.jasdewstarfield.brnquest.author.EntryCreationPolicy.rewardPolicy(snapshot.book(), typeId, schema.rawConfig()),
                snapshot.book().settings().rewardTeam());
        baseline = values();
        host.focus(form().identityField("id"));
    }

    void render(GuiGraphics graphics, QuestDefinition quest, int mouseX, int mouseY) {
        refreshTypedPropertySubmission();
        if (!open()) return;
        boolean enabled = !host.session().busy();
        Map<String, String> issues = typedPropertyLocalIssues();
        Component heading = typedPropertyMessage == null ? firstTypedIssue(issues) : typedPropertyMessage;
        if (heading == null) heading = Component.translatable("screen.brnquest.editor.typed.property.heading");
        List<EditorPropertyPanel.RowContent> rows = new ArrayList<>();
        rows.add(EditorPropertyPanel.section(font, "screen.brnquest.editor.section.identity"));
        rows.add(EditorPropertyPanel.readOnly(font, "screen.brnquest.editor.typed.property.type",
                typedTypeName(quest, originalId()), 68));
        rows.add(EditorPropertyPanel.text(font, form().identityField("id"),
                "screen.brnquest.editor.typed.property.id", 68,
                form().serverIssues().get("id"), enabled));
        hide();
        typedConfigRenderedFields.clear();
        List<ConfigFieldDescriptor> descriptors = form().schema() == null
                ? List.of() : form().schema().fields();
        rows.add(EditorPropertyPanel.section(font, "screen.brnquest.editor.section.configuration"));
        for (int index = 0; index < Math.min(descriptors.size(), MAX_TYPED_CONFIG_FIELDS); index++) {
            ConfigFieldDescriptor descriptor = descriptors.get(index);
            int fieldIndex = index;
            String issue = issues.getOrDefault(descriptor.key(),
                    form().serverIssues().get(descriptor.key()));
            // Each descriptor reserves the actual wrapped label height; other rows keep their compact spacing.
            rows.add(EditorPropertyPanel.sized(w -> {
                var row = typedConfigRowLayout(descriptor, fieldIndex, 0, 0, w, issue);
                return Math.max(row.label().bottom(), row.field().bottom());
            }, (g, x, y, w) -> renderTypedConfigRow(g, descriptor, fieldIndex, x, y, w, issue, mouseX, mouseY)));
        }
        if (form().schema() != null && form().schema().rawFallback()) {
            rows.add((g, x, y, w) -> {
                if (typedPropertyRawEditable()) renderTypedRawConfigRow(g, x, y, w, typedPropertyRawIssue(), mouseX, mouseY);
                else g.drawString(font, Component.translatable("screen.brnquest.editor.typed.property.raw_preserved"),
                        x, y + 5, 0xFFFFA070, false);
            });
        }
        rows.add(EditorPropertyPanel.section(font, "screen.brnquest.editor.section.completion"));
        if (host.kind() == QuestTypedEntryKind.TASK) {
            rows.add((g, x, y, w) -> renderTypedToggleRow(g, "screen.brnquest.editor.typed.property.optional",
                    optional(), x, y, w, mouseX, mouseY));
        } else {
            rows.add((g, x, y, w) -> {
                EditorPropertyFormLayout.Row row = EditorPropertyFormLayout.row(x, y, w, 68);
                drawTypedLabel(g, "screen.brnquest.editor.typed.property.claim_policy", row.label(),
                        form().serverIssues().get("claim_policy"));
                host.dropdownButton(g, row.field(), authorValueLabel("claim", form().claim()), enabled, mouseX, mouseY);
            });
            rows.add((g, x, y, w) -> renderTypedToggleRow(g, "screen.brnquest.editor.typed.property.team_reward",
                    teamReward(), x, y, w, mouseX, mouseY));
        }
        host.panel(graphics, heading, typedPropertyMessage == null && issues.isEmpty()
                        && form().serverIssues().isEmpty() ? 0xFFFFFFFF : 0xFFFFA070, rows,
                Component.translatable(renameArmed()
                        ? "screen.brnquest.editor.typed.property.confirm_rename" : "gui.done"),
                renameArmed()
                        ? EditorButton.Tone.WARNING : EditorButton.Tone.PRIMARY, mouseX, mouseY);
        captureTypedPropertyInteractionFrame(descriptors);
    }

    private Component typedTypeName(QuestDefinition quest, ResourceLocation typedId) {
        QuestTypedEntryKind.Entry entry = host.kind().entry(quest, typedId);
        if (entry == null) return Component.literal(typeId() == null
                ? "" : typeId().toString());
        return host.kind() == QuestTypedEntryKind.TASK
                ? ClientTaskPresentationRegistry.get(entry.typeId()).typeName(ApiViews.task(entry.task()))
                : ClientRewardPresentationRegistry.get(entry.typeId()).typeName(ApiViews.reward(entry.reward()));
    }

    private EditorPropertyFormLayout.Row typedConfigRowLayout(ConfigFieldDescriptor descriptor, int index,
                                                             int left, int top, int width, String issue) {
        Component label = Component.translatable(descriptor.labelKey().isBlank()
                ? typedConfigLabel(descriptor.key()) : descriptor.labelKey());
        var editor = ClientConfigEditors.find(typeId(), descriptor.key());
        if (editor.isPresent()) {
            String value = form().configField(index).getValue();
            var factory = editor.orElseThrow();
            int iconWidth = factory.icon(value).map(icon -> icon.width(font)).orElse(0);
            return EditorPropertyRow.action(font, label, factory.label(value), left, top, width, 68, iconWidth, issue);
        }
        if (descriptor.valueType() == ConfigValueType.ITEM_STACK && descriptor.serverSource().isEmpty()) {
            return EditorPropertyRow.action(font, label,
                    Component.translatable("screen.brnquest.editor.typed.property.select_item"),
                    left, top, width, 68, 16, issue);
        }
        return EditorPropertyFormLayout.row(left, top, width, 68, EditorPropertyRow.height(font, label, 68, issue));
    }

    private void renderTypedConfigRow(GuiGraphics graphics, ConfigFieldDescriptor descriptor, int index,
                                      int left, int top, int width, String issue, int mouseX, int mouseY) {
        Component label = Component.translatable(descriptor.labelKey().isBlank()
                ? typedConfigLabel(descriptor.key()) : descriptor.labelKey());
        EditorPropertyFormLayout.Row row = typedConfigRowLayout(descriptor, index, left, top, width, issue);
        // Capture the actual painted field: stacked buttons cannot reuse the old side-by-side input rectangle.
        typedConfigRenderedFields.put(index, row.field());
        EditorPropertyRow.label(graphics, font, label, row.label(), issue);
        // Types may supply either a translated help key or existing literal help text.
        if (row.label().containsExclusive(mouseX, mouseY)) {
            // Keep long descriptor labels and their help available alongside the fully wrapped descriptor label.
            var help = new ArrayList<Component>();
            help.add(Component.translatable(descriptor.labelKey().isBlank() ? typedConfigLabel(descriptor.key()) : descriptor.labelKey()));
            if (!descriptor.helpText().isBlank()) help.add(Component.translatable(descriptor.helpText()));
            if (issue != null && !issue.isBlank()) help.add(Component.literal(issue));
            host.tooltip(help);
        }
        EditorTextField field = form().configField(index);
        if (ClientConfigEditors.find(typeId(), descriptor.key()).isPresent()) {
            var editor = ClientConfigEditors.find(typeId(), descriptor.key()).orElseThrow();
            var icon = editor.icon(field.getValue());
            if (icon.isPresent()) host.action(graphics, row.field(),
                    EditorButton.Definition.iconAndText(editor.label(field.getValue()), editor.label(field.getValue()), icon.get()),
                    !host.session().busy(), EditorButton.Tone.NEUTRAL, mouseX, mouseY);
            else host.text(graphics, row.field(), editor.label(field.getValue()), null,
                    !host.session().busy(), EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        } else if (descriptor.valueType() == ConfigValueType.INTEGER_VECTOR3) {
            var vector = yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorVectorRow.layout(row.field(), descriptor.serverSource().isPresent());
            for (int axis = 0; axis < 3; axis++) {
                UiRect bounds = vector.axes().get(axis);
                graphics.drawString(font, new String[]{"X", "Y", "Z"}[axis], bounds.left(), bounds.top() + 5, GraystonePalette.SECONDARY, false);
                form().vectorField(index, axis).show(new UiRect(bounds.left() + 8, bounds.top(), bounds.right(), bounds.bottom()), !host.session().busy());
            }
            if (vector.current() != null) host.action(graphics, vector.current(),
                    EditorButton.Definition.iconOnly(Component.translatable("screen.brnquest.field.current"),
                            Component.translatable("screen.brnquest.field.current"), QuestActionIcons.symbol(Component.literal("@"))),
                    !host.session().busy(), EditorButton.Tone.PRIMARY, mouseX, mouseY);
        } else if (descriptor.serverSource().isPresent()) {
            host.action(graphics, row.field(), EditorButton.Definition.iconAndText(
                            Component.literal(field.getValue()), null, QuestActionIcons.named("detail")),
                    !host.session().busy(), EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        } else if (descriptor.valueType() == ConfigValueType.BOOLEAN) {
            host.text(graphics, row.field(), booleanValue(field.getValue())
                            ? Component.translatable("options.on") : Component.translatable("options.off"),
                    null, !host.session().busy(), EditorButton.Tone.NEUTRAL, mouseX, mouseY);
        } else if (descriptor.valueType() == ConfigValueType.ENUM) {
            host.dropdownButton(graphics, row.field(), ConfigFieldLabels.value(descriptor, field.getValue()), !host.session().busy(), mouseX, mouseY);
        } else if (descriptor.valueType() == ConfigValueType.ITEM_STACK) {
            ItemStack stack = host.item(originalId(), field.getValue());
            Component select = Component.translatable("screen.brnquest.editor.typed.property.select_item");
            EditorIcon icon = stack.isEmpty()
                    ? QuestActionIcons.named("plus") : EditorIcon.item(stack);
            EditorButton.Definition definition = EditorButton.Definition.iconAndText(select, select, icon);
            host.action(graphics, row.field(), definition,
                    !host.session().busy(), EditorButton.Tone.NEUTRAL, mouseX, mouseY);
            // This icon opens an editor; it is not a recipe lookup target.
        } else {
            field.show(row.field(), !host.session().busy());
        }
    }

    private void renderTypedToggleRow(GuiGraphics graphics, String labelKey, boolean value,
                                      int left, int top, int width, int mouseX, int mouseY) {
        EditorPropertyFormLayout.Row row = EditorPropertyFormLayout.row(left, top, width, 68);
        drawTypedLabel(graphics, labelKey, row.label());
        host.text(graphics, row.field(), value
                        ? Component.translatable("options.on") : Component.translatable("options.off"),
                null, !host.session().busy(), EditorButton.Tone.NEUTRAL, mouseX, mouseY);
    }

    private void renderTypedRawConfigRow(GuiGraphics graphics, int left, int top, int width, String issue,
                                         int mouseX, int mouseY) {
        EditorPropertyFormLayout.Row row = EditorPropertyFormLayout.row(left, top, width, 68);
        drawTypedLabel(graphics, "screen.brnquest.editor.typed.property.raw_config", row.label(), issue);
        Component edit = Component.translatable(
                "screen.brnquest.editor.typed.property.edit_raw", form().rawConfig().size());
        host.action(graphics, row.field(), EditorButton.Definition.iconAndText(
                        edit, edit, QuestActionIcons.named("raw")),
                !host.session().busy(), EditorButton.Tone.NEUTRAL, mouseX, mouseY);
    }

    private void drawTypedLabel(GuiGraphics graphics, String labelKey, UiRect bounds) {
        drawTypedLabel(graphics, labelKey, bounds, null);
    }

    private void drawTypedLabel(GuiGraphics graphics, String labelKey, UiRect bounds, String issue) {
        EditorPropertyRow.label(graphics, font, Component.translatable(labelKey), bounds, issue);
    }

    private String typedConfigLabel(String key) {
        return switch (key) {
            case "item" -> "screen.brnquest.editor.config.item";
            case "matcher" -> "screen.brnquest.editor.config.matcher";
            case "required_entries" -> "screen.brnquest.editor.config.required_entries";
            case "count" -> "screen.brnquest.editor.config.count";
            case "consume_items" -> "screen.brnquest.editor.config.consume_items";
            // Built-in crafting progress uses a localized label instead of exposing its storage key.
            case "only_from_crafting" -> "screen.brnquest.editor.config.only_from_crafting";
            case "title" -> "screen.brnquest.editor.config.title";
            case "script_id" -> "screen.brnquest.editor.config.script_id";
            case "message_id" -> "screen.brnquest.editor.config.message_id";
            default -> key;
        };
    }

    private void captureTypedPropertyInteractionFrame(List<ConfigFieldDescriptor> descriptors) {
        List<QuestTypedPropertySection.FieldHit> fields = new ArrayList<>();
        for (int index = 0; index < Math.min(descriptors.size(), MAX_TYPED_CONFIG_FIELDS); index++) {
            UiRect painted = typedConfigRenderedFields.get(index);
            UiRect bounds = painted == null ? null : host.visible(painted);
            if (bounds != null) fields.add(new QuestTypedPropertySection.FieldHit(index, descriptors.get(index), bounds));
        }
        int semanticsTop = typedPropertySemanticsTop();
        boolean raw = form().schema() != null
                && form().schema().rawFallback() && typedPropertyRawEditable();
        boolean task = host.kind() == QuestTypedEntryKind.TASK;
        captureInteractionFrame(new QuestTypedPropertySection.InteractionFrame(
                host.frame(), host.kind(), host.cancel(), host.save(),
                task ? null : host.visible(typedPropertySemanticBounds(semanticsTop)), fields,
                raw ? host.visible(typedPropertyRawBounds()) : null,
                task ? host.visible(typedPropertySemanticBounds(semanticsTop)) : null,
                task ? null : host.visible(typedPropertySemanticBounds(semanticsTop + 22))));
    }

    boolean handleClick(double mouseX, double mouseY) {
        if (!host.session().editable() || host.session().busy()) return true;
        if (!host.viewport().contains(mouseX, mouseY) && !host.cancel().contains(mouseX, mouseY)
                && !host.save().contains(mouseX, mouseY)) return true;
        Optional<QuestTypedPropertySection.Intent> result = click(
                host.frame(), host.kind(), mouseX, mouseY);
        if (result.isPresent()) {
            QuestTypedPropertySection.Intent intent = result.orElseThrow();
            switch (intent.action()) {
                case CANCEL -> closeEditor();
                case SUBMIT -> prepareTypedPropertyEdit();
                case CLAIM -> host.dropdown(intent.anchor(), REWARD_CLAIM_POLICIES,
                        form().identityField("claim")::setValue, value -> authorValueLabel("claim", value));
                case BOOLEAN -> {
                    EditorTextField field = form().configField(intent.fieldIndex());
                    field.setValue(Boolean.toString(!booleanValue(field.getValue())));
                }
                case ENUM -> {
                    var descriptor = form().schema().fields().get(intent.fieldIndex());
                    host.dropdown(intent.anchor(), intent.values(),
                            form().configField(intent.fieldIndex())::setValue,
                            value -> ConfigFieldLabels.value(descriptor, value));
                }
                case SERVER_CURRENT -> {
                    int index = intent.fieldIndex();
                    var descriptor = form().schema().fields().get(index);
                    String requestId = beginCurrentRequest(index);
                    host.currentRequest(requestId, descriptor.serverSource().orElseThrow().toString(), form().configValue(index));
                }
                case SERVER_FIELD -> {
                    var descriptor = form().schema().fields().get(intent.fieldIndex());
                    var field = form().configField(intent.fieldIndex());
                    host.children().server(descriptor, field.getValue(), form().currentConfig(), returning(value -> form().setConfigValue(intent.fieldIndex(), value)));
                }
                case ITEM -> openTypedPropertyItemSelector(intent.fieldIndex());
                case MATCHER -> openTypedPropertyRawEditor();
                case CUSTOM -> {
                    var descriptor = form().schema().fields().get(intent.fieldIndex());
                    host.children().custom(typeId(), descriptor.key(), form().currentConfig(), returning(patch -> {
                        form().applyConfigPatch(patch);
                        host.invalidateIcon(originalId());
                        typedPropertyMessage = null;
                    }));
                }
                case RAW -> openTypedPropertyRawEditor();
                case OPTIONAL -> toggleOptional();
                case TEAM_REWARD -> toggleTeamReward();
            }
            return true;
        }
        return false;
    }

    private void openTypedPropertyItemSelector(int fieldIndex) {
        if (Minecraft.getInstance() == null) return;
        host.children().items(returning(stack -> {
            if (Minecraft.getInstance().level == null || fieldIndex >= MAX_TYPED_CONFIG_FIELDS) return;
            form().setConfigValue(fieldIndex,
                    stack.copyWithCount(1).save(Minecraft.getInstance().level.registryAccess()).toString());
            host.invalidateIcon(originalId());
        }));
    }

    private void openTypedPropertyRawEditor() {
        if (Minecraft.getInstance() == null || !typedPropertyRawEditable()) return;
        host.children().raw(form().rawConfig(), returning(config -> {
            form().replaceRawConfig(config);
            // A corrected local value supersedes old field diagnostics; the server will return
            // fresh Codec diagnostics when the containing property form is submitted.
            form().clearNonIdentityServerIssues();
            typedPropertyMessage = null;
        }));
    }

    private void prepareTypedPropertyEdit() {
        QuestTypedPropertySection.Preparation preparation =
                prepare(host.kind(), typedPropertyLocalIssues());
        if (preparation.status() == QuestTypedPropertySection.PreparationStatus.INVALID_ID) {
            typedPropertyMessage = Component.translatable("screen.brnquest.editor.typed.property.invalid_id");
            host.focus(form().identityField("id"));
            return;
        }
        if (preparation.status() == QuestTypedPropertySection.PreparationStatus.LOCAL_ISSUE) {
            typedPropertyMessage = Component.literal(preparation.fieldKey() + ": " + preparation.issue());
            focusTypedConfigField(preparation.fieldKey());
            return;
        }
        if (preparation.status() == QuestTypedPropertySection.PreparationStatus.CONFIRM_RENAME) {
            typedPropertyMessage = Component.translatable("screen.brnquest.editor.typed.property.rename_warning");
            return;
        }
        if (preparation.status() == QuestTypedPropertySection.PreparationStatus.CLAIM_REQUIRED) {
            typedPropertyMessage = Component.translatable("screen.brnquest.editor.typed.property.claim_required");
            form().serverIssues().put("claim_policy", typedPropertyMessage.getString());
            host.focus(form().identityField("claim"));
            return;
        }
        QuestTypedPropertySection.Submission submission = preparation.submission();
        form().serverIssues().clear();
        String action = (creating() ? "ADD_" : "UPDATE_") + host.kind().actionPrefix();
        if (!host.submit(action, host.quest(), submission)) return;
        markSubmissionPending();
        typedPropertyMessage = Component.translatable("screen.brnquest.editor.typed.property.submitting");
        host.invalidateIcon(originalId());
        host.invalidateIcon(submission.replacementId());
    }

    void closeEditor() {
        typedPropertyMessage = null;
        close();
        host.focus(null);
    }

    private int typedPropertyFieldCount() {
        return form().schema() == null ? 0
                : Math.min(form().schema().fields().size(), MAX_TYPED_CONFIG_FIELDS);
    }

    private int typedPropertySemanticsTop() {
        int raw = form().schema() != null
                && form().schema().rawFallback() ? 1 : 0;
        return host.row(5 + typedPropertyFieldCount() + raw).top();
    }

    private UiRect typedPropertyRawBounds() {
        UiRect bounds = host.row(4 + typedPropertyFieldCount());
        return EditorPropertyFormLayout.row(bounds.left(), bounds.top(), bounds.width(), 68).field();
    }

    private UiRect typedPropertySemanticBounds(int top) {
        return EditorPropertyFormLayout.row(detailLeft() + 10, top, detailsWidth() - 24, 68).field();
    }

    private boolean typedPropertyRawEditable() {
        if (form().schema() == null || !form().schema().rawFallback()
                || typeId() == null) {
            return false;
        }
        return host.kind() == QuestTypedEntryKind.TASK
                ? TaskTypeRegistry.get(typeId()) != null
                : RewardTypeRegistry.get(typeId()) != null;
    }

    private String typedPropertyRawIssue() {
        return form().serverIssues().entrySet().stream()
                .filter(entry -> !"id".equals(entry.getKey()) && !"claim_policy".equals(entry.getKey()))
                .map(Map.Entry::getValue).findFirst().orElse(null);
    }

    private Map<String, String> typedPropertyLocalIssues() {
        if (form().schema() == null) return Map.of();
        Map<String, String> issues = new LinkedHashMap<>(form().localIssues());
        Map<String, String> config = form().currentConfig();
        if (Minecraft.getInstance() != null && Minecraft.getInstance().level != null) {
            for (ConfigFieldDescriptor field : form().schema().fields()) {
                String value = config.getOrDefault(field.key(), "");
                if (field.valueType() != ConfigValueType.ITEM_STACK || value.isBlank()) continue;
                try {
                    ItemStack stack = ItemStack.parseOptional(Minecraft.getInstance().level.registryAccess(), TagParser.parseTag(value));
                    if (stack.isEmpty()) issues.putIfAbsent(field.key(), "ItemStack does not contain a registered item");
                } catch (Exception exception) {
                    issues.putIfAbsent(field.key(), "ItemStack SNBT is invalid");
                }
            }
        }
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(issues));
    }

    private Component firstTypedIssue(Map<String, String> localIssues) {
        Map.Entry<String, String> issue = !form().serverIssues().isEmpty()
                ? form().serverIssues().entrySet().iterator().next()
                : localIssues.isEmpty() ? null : localIssues.entrySet().iterator().next();
        return issue == null ? null : Component.literal("! " + issue.getKey() + ": " + issue.getValue());
    }

    private void focusTypedConfigField(String fieldKey) {
        List<ConfigFieldDescriptor> fields = form().schema() == null
                ? List.of() : form().schema().fields();
        for (int index = 0; index < Math.min(fields.size(), MAX_TYPED_CONFIG_FIELDS); index++) {
            if (fields.get(index).key().equals(fieldKey)) {
                if (fields.get(index).valueType() == ConfigValueType.INTEGER_VECTOR3) {
                    host.focus(form().vectorField(index, 0));
                } else if (fields.get(index).valueType() != ConfigValueType.ITEM_STACK) {
                    host.focus(form().configField(index));
                }
                return;
            }
        }
    }

    private void refreshTypedPropertySubmission() {
        if (!submissionPending()) return;
        EditorSessionView editor = host.session();
        if (editor.busy()) return;
        if (editor.mode() == ClientEditorState.Mode.EDITING) {
            closeEditor();
            return;
        }
        if (editor.mode() != ClientEditorState.Mode.ERROR) return;
        clearSubmissionPending();
        typedPropertyMessage = EditorDiagnosticPresentation.operationError(editor.statusCode());
        form().serverIssues().clear();
        for (EditorPublishReviewModel.Diagnostic diagnostic : host.diagnostics()) {
            String path = diagnostic.path() == null ? "" : diagnostic.path();
            String fieldKey = path.startsWith("config.") ? path.substring("config.".length()) : path;
            if (!fieldKey.isBlank()) form().serverIssues().putIfAbsent(fieldKey,
                    EditorDiagnosticPresentation.diagnostic(diagnostic).getString());
        }
        if (!form().serverIssues().isEmpty()) {
            String fieldKey = form().serverIssues().keySet().iterator().next();
            if ("id".equals(fieldKey)) host.focus(form().identityField("id"));
            else if ("claim_policy".equals(fieldKey)) {
                host.focus(form().identityField("claim"));
            }
            else focusTypedConfigField(fieldKey);
        }
    }

    private int detailLeft() { return host.layout().detailLeft(); }
    private int detailsWidth() { return host.layout().detailsWidth(); }
    private static Component authorValueLabel(String category, String value) {
        String key = "screen.brnquest.editor.value." + category + "." + value;
        return net.minecraft.client.resources.language.I18n.exists(key) ? Component.translatable(key) : Component.literal(value);
    }
    private static boolean booleanValue(String value) { return "true".equalsIgnoreCase(value) || "1b".equalsIgnoreCase(value); }
    private static ResourceLocation suggestId(QuestBookDefinition book, String stem) { return EditorObjectIds.suggest(book, stem); }
}
