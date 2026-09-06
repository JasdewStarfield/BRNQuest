package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestTypedEntryListSectionTest {
    @Test void onlyTheExactRenderedQuestFrameMayReceiveInput() {
        ResourceLocation book = ResourceLocation.parse("example:book");
        ResourceLocation quest = ResourceLocation.parse("example:quest");
        QuestScreenFrameIdentity rendered = new QuestScreenFrameIdentity(book, "rev-a", true, 800, 480);

        assertTrue(QuestTypedEntryListSection.acceptsFrame(rendered, quest, QuestTypedEntryKind.TASK,
                rendered, quest, QuestTypedEntryKind.TASK));
        assertFalse(QuestTypedEntryListSection.acceptsFrame(rendered, quest, QuestTypedEntryKind.TASK,
                new QuestScreenFrameIdentity(book, "rev-b", true, 800, 480), quest, QuestTypedEntryKind.TASK));
        assertFalse(QuestTypedEntryListSection.acceptsFrame(rendered, quest, QuestTypedEntryKind.TASK,
                rendered, ResourceLocation.parse("example:other_quest"), QuestTypedEntryKind.TASK));
        assertFalse(QuestTypedEntryListSection.acceptsFrame(rendered, quest, QuestTypedEntryKind.TASK,
                rendered, quest, QuestTypedEntryKind.REWARD));
    }
}
