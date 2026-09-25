package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.reward.RewardTypes;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestTypePickerModelTest {
    @Test void frameRejectsSelectionsFromAnObsoleteRevisionOrViewport() {
        ResourceLocation type = ResourceLocation.parse("example:type");
        QuestScreenFrameIdentity rendered = new QuestScreenFrameIdentity(
                ResourceLocation.parse("example:book"), "rev-a", true, 800, 480);
        QuestTypePickerModel.Frame frame = new QuestTypePickerModel.Frame(rendered,
                QuestTypedEntryKind.TASK, List.of(new QuestTypePickerModel.Entry(
                type, false, QuestTypePickerModel.Route.PROPERTY_FORM)));

        assertEquals(type, frame.select(rendered, QuestTypedEntryKind.TASK, type).orElseThrow().typeId());
        assertTrue(frame.select(new QuestScreenFrameIdentity(rendered.bookId(), "rev-b", true, 800, 480),
                QuestTypedEntryKind.TASK, type).isEmpty());
        assertTrue(frame.select(new QuestScreenFrameIdentity(rendered.bookId(), "rev-a", true, 640, 480),
                QuestTypedEntryKind.TASK, type).isEmpty());
        assertTrue(frame.select(rendered, QuestTypedEntryKind.REWARD, type).isEmpty());
        assertTrue(frame.select(rendered, QuestTypedEntryKind.TASK,
                ResourceLocation.parse("missing:type")).isEmpty());
    }

    @Test void builtInAndExtensionTypesChooseTheirExistingCreationFlows() {
        assertEquals(QuestTypePickerModel.Route.CHOICE_SELECTOR,
                QuestTypePickerModel.route(QuestTypedEntryKind.TASK,
                        ResourceLocation.parse("brnquest:item")));
        assertEquals(QuestTypePickerModel.Route.ITEM_SELECTOR,
                QuestTypePickerModel.route(QuestTypedEntryKind.REWARD,
                        ResourceLocation.parse("brnquest:item")));
        assertEquals(QuestTypePickerModel.Route.DIRECT,
                QuestTypePickerModel.route(QuestTypedEntryKind.TASK,
                        ResourceLocation.parse("brnquest:checkmark")));
        assertEquals(QuestTypePickerModel.Route.PROPERTY_FORM,
                QuestTypePickerModel.route(QuestTypedEntryKind.TASK,
                        ResourceLocation.parse("example:extension")));
    }

    @Test void legacyLevelsRewardIsHiddenOnlyFromCreationChoices() {
        var candidates = QuestTypePickerModel.creatableTypeCandidates(
                List.of(RewardTypes.XP, RewardTypes.XP_LEVELS), QuestTypedEntryKind.REWARD::addable);
        assertEquals(List.of(RewardTypes.XP), candidates);
        assertTrue(QuestTypedEntryKind.REWARD.known(RewardTypes.XP_LEVELS));
        assertTrue(!QuestTypedEntryKind.REWARD.addable(RewardTypes.XP_LEVELS));
        assertTrue(!QuestTypedEntryKind.TASK.addable(
                ResourceLocation.parse("brnquest:item_choice")));
    }
}
