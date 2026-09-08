package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;
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

    @Test
    void interactionIntentRequiresTheExactRenderedFrameAndKind() {
        QuestTypedPropertySection section = existingTask();
        QuestScreenFrameIdentity frame = new QuestScreenFrameIdentity(
                ResourceLocation.fromNamespaceAndPath("test", "book"), "rev-1", true, 800, 600);
        ConfigFieldDescriptor mode = ConfigFieldDescriptor.enumeration("mode", List.of("safe", "fast"));
        section.captureInteractionFrame(new QuestTypedPropertySection.InteractionFrame(frame,
                QuestTypedEntryKind.TASK, new UiRect(0, 0, 9, 9), new UiRect(10, 0, 19, 9), null,
                List.of(new QuestTypedPropertySection.FieldHit(0, mode, new UiRect(20, 0, 39, 9))),
                null, new UiRect(40, 0, 59, 9), null));

        QuestTypedPropertySection.Intent intent = section.click(frame, QuestTypedEntryKind.TASK, 25, 5).orElseThrow();
        assertEquals(QuestTypedPropertySection.Action.ENUM, intent.action());
        assertEquals(List.of("safe", "fast"), intent.values());
        assertTrue(section.click(new QuestScreenFrameIdentity(frame.bookId(), "rev-2", true, 800, 600),
                QuestTypedEntryKind.TASK, 25, 5).isEmpty());
        assertTrue(section.click(frame, QuestTypedEntryKind.REWARD, 25, 5).isEmpty());
    }

    @Test void externalServerFieldUsesTheGenericPickerAndRejectsAStaleFrame() {
        var section = existingTask();
        var frame = new QuestScreenFrameIdentity(ResourceLocation.parse("test:book"),"rev-1",true,800,600);
        var field = ConfigFieldDescriptor.field("external_selector",ConfigValueType.TEXT)
                .withServerSource(ResourceLocation.parse("external:source")).withLabel("external.label").withDefault("#external:group");
        section.captureInteractionFrame(new QuestTypedPropertySection.InteractionFrame(frame,QuestTypedEntryKind.TASK,
                new UiRect(0,0,9,9),new UiRect(10,0,19,9),null,
                List.of(new QuestTypedPropertySection.FieldHit(0,field,new UiRect(20,0,39,9))),null,null,null));
        assertEquals(QuestTypedPropertySection.Action.SERVER_FIELD,section.click(frame,QuestTypedEntryKind.TASK,25,5).orElseThrow().action());
        assertEquals(ResourceLocation.parse("external:source"),field.serverSource().orElseThrow());
        assertTrue(section.click(new QuestScreenFrameIdentity(frame.bookId(),"rev-2",true,800,600),QuestTypedEntryKind.TASK,25,5).isEmpty());
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
