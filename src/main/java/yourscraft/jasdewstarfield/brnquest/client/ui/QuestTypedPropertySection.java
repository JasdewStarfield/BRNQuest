package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.EditorTextField;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigEditorSchema;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Coordinates one typed task/reward property form without owning protocol dispatch.
 * The parent Screen converts a validated immutable submission into the existing server mutation.
 */
final class QuestTypedPropertySection {
    enum PreparationStatus { INVALID_ID, LOCAL_ISSUE, CONFIRM_RENAME, CLAIM_REQUIRED, READY }

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
        form.openNew(schema, id.toString(), "manual");
    }

    void toggleOptional() { optional = !optional; }
    void toggleTeamReward() { teamReward = !teamReward; }
    void markSubmissionPending() { submissionPending = true; }
    void clearSubmissionPending() { submissionPending = false; }

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

    void close() {
        open = false;
        originalId = null;
        typeId = null;
        creating = false;
        optional = false;
        teamReward = false;
        renameArmed = false;
        submissionPending = false;
        form.close();
    }
}
