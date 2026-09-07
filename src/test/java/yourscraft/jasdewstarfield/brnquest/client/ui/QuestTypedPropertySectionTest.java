package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigEditorSchema;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigFieldDescriptor;
import yourscraft.jasdewstarfield.brnquest.editor.ConfigValueType;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestTypedPropertySectionTest {
    @Test
    void renameRequiresASecondPreparationWithoutDiscardingDraft() {
        QuestTypedPropertySection section = existingTask();
        section.form().openExisting(schema(), "test:renamed", "manual");
        section.form().setConfigValue(0, "8");

        assertEquals(QuestTypedPropertySection.PreparationStatus.CONFIRM_RENAME,
                section.prepare(QuestTypedEntryKind.TASK, Map.of()).status());
        QuestTypedPropertySection.Preparation ready = section.prepare(QuestTypedEntryKind.TASK, Map.of());

        assertEquals(QuestTypedPropertySection.PreparationStatus.READY, ready.status());
        assertEquals("8", ready.submission().config().get("count"));
    }

    @Test
    void serverRejectionCanClearPendingWithoutDiscardingInput() {
        QuestTypedPropertySection section = existingTask();
        section.form().setConfigValue(0, "11");
        section.markSubmissionPending();
        section.form().serverIssues().put("count", "Rejected");
        section.clearSubmissionPending();

        assertFalse(section.submissionPending());
        assertEquals("11", section.form().configValue(0));
        assertEquals("Rejected", section.form().serverIssues().get("count"));
    }

    @Test
    void newRewardProducesImmutableSubmissionSemantics() {
        QuestTypedPropertySection section = new QuestTypedPropertySection(4);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("test", "reward");
        section.openNew(id, ResourceLocation.fromNamespaceAndPath("test", "reward_type"), schema());
        section.toggleTeamReward();

        QuestTypedPropertySection.Submission submission =
                section.prepare(QuestTypedEntryKind.REWARD, Map.of()).submission();

        assertNotNull(submission);
        assertEquals(1, submission.semanticFlag());
        assertEquals("manual", submission.claimPolicy());
        assertTrue(section.creating());
    }

    private static QuestTypedPropertySection existingTask() {
        QuestTypedPropertySection section = new QuestTypedPropertySection(4);
        var task = new yourscraft.jasdewstarfield.brnquest.data.TaskDefinition(
                ResourceLocation.fromNamespaceAndPath("test", "book"),
                ResourceLocation.fromNamespaceAndPath("test", "task"),
                ResourceLocation.fromNamespaceAndPath("test", "type"), Map.of("count", "2"), false);
        section.openExisting(QuestTypedEntryKind.Entry.task(task), schema());
        return section;
    }

    private static ConfigEditorSchema schema() {
        return new ConfigEditorSchema(ConfigEditorSchema.Kind.TASK,
                ResourceLocation.fromNamespaceAndPath("test", "type"),
                List.of(ConfigFieldDescriptor.field("count", ConfigValueType.INTEGER)),
                Map.of("count", "2"), List.of(), false);
    }
}
