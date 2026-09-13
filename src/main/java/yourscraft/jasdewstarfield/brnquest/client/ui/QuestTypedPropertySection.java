package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorTextField;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigEditorSchema;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigFieldDescriptor;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigValueType;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Coordinates one typed task/reward property form without owning protocol dispatch.
 * The parent Screen converts a validated immutable submission into the existing server mutation.
 */
final class QuestTypedPropertySection {
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

    void bind(Font font, Consumer<EditorTextField> register) { form.bind(font, register); }
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
}
