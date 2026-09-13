package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.client.ui.component.UiRect;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Locks detail input to the geometry and revision that produced the visible frame. */
class QuestDetailsInteractionTest {
    private static final ResourceLocation BOOK = id("book");
    private static final ResourceLocation QUEST = id("quest");
    private static final ResourceLocation TASK = id("task");
    private static final ResourceLocation REWARD = id("reward");
    private static final UiRect PANEL = new UiRect(600, 20, 800, 580);

    @Test void relationNavigationWorksInPreviewButRejectsRightClicksAndStaleFrames() {
        var interaction = frame(false, false);
        interaction.relationToggle(QuestDetailsInteraction.Action.OPEN_UPSTREAM, new UiRect(620, 60, 700, 80));
        interaction.finish();
        assertEquals(QuestDetailsInteraction.Action.OPEN_UPSTREAM,
                interaction.click(identity("r1", 800), 650, 70, 0).intent().action());
        assertNull(interaction.click(identity("r1", 800), 650, 70, 1).intent());
        assertFalse(interaction.click(identity("r2", 800), 650, 70, 0).consumed());
        interaction.invalidate(); assertFalse(interaction.click(identity("r1", 800), 650, 70, 0).consumed());
    }

    @Test void candidateButtonWinsOverTheContainingTaskRowEvenInPreview() {
        QuestDetailsInteraction interaction = frame(false, false);
        interaction.task(TASK, new UiRect(620, 100, 780, 124),
                new UiRect(641, 104, 655, 120), QuestDetailsInteraction.Action.OPEN_ITEM_SLOT_SELECTION);
        interaction.finish();

        QuestDetailsInteraction.ClickResult result = interaction.click(identity("r1", 800), 648, 110, 0);

        assertTrue(result.consumed());
        assertEquals(QuestDetailsInteraction.Action.OPEN_SUBMISSION_CHOICES, result.intent().action());
        assertEquals(TASK, result.intent().targetId());
    }

    @Test void actionableRowsAndRewardsReturnOnlySemanticIds() {
        QuestDetailsInteraction interaction = frame(false, true);
        interaction.task(TASK, new UiRect(620, 100, 780, 124), null,
                QuestDetailsInteraction.Action.SUBMIT_TASK);
        interaction.reward(REWARD, new UiRect(620, 150, 644, 174));
        interaction.finish();

        assertEquals(QuestDetailsInteraction.Action.SUBMIT_TASK,
                interaction.click(identity("r1", 800), 700, 112, 0).intent().action());
        assertEquals(REWARD, interaction.click(identity("r1", 800), 632, 162, 0).intent().targetId());
    }

    @Test void staleRevisionAndResizeCannotReuseOldHitGeometry() {
        QuestDetailsInteraction interaction = frame(false, true);
        interaction.statusActions(new UiRect(620, 60, 680, 72), new UiRect(750, 60, 764, 72));
        interaction.finish();

        assertFalse(interaction.click(identity("r2", 800), 640, 66, 0).consumed());
        assertFalse(interaction.click(identity("r1", 801), 640, 66, 0).consumed());
        assertEquals(QuestDetailsInteraction.Action.COMPLETE_QUEST,
                interaction.click(identity("r1", 800), 640, 66, 0).intent().action());
    }

    @Test void rightClickEditsOnlyVisibleQuickTextAndNeverSubmitsGameplay() {
        QuestDetailsInteraction interaction = frame(true, true);
        interaction.textAreas(Map.of("DESCRIPTION", new UiRect(620, 80, 780, 98)));
        interaction.task(TASK, new UiRect(620, 100, 780, 124), null,
                QuestDetailsInteraction.Action.SUBMIT_TASK);
        interaction.finish();

        QuestDetailsInteraction.Intent quick = interaction.click(identity("r1", 800), 700, 90, 1).intent();
        assertEquals(QuestDetailsInteraction.Action.QUICK_EDIT_TEXT, quick.action());
        assertEquals("DESCRIPTION", quick.textArea());
        assertNull(interaction.click(identity("r1", 800), 700, 112, 1).intent());
    }

    @Test void rewardOptionsAreReadOnlyAndRejectStaleGeometry() {
        var interaction = frame(false,false);
        interaction.rewardOptions(REWARD,new UiRect(645,154,659,170));
        interaction.finish();
        assertEquals(QuestDetailsInteraction.Action.OPEN_REWARD_OPTIONS,
                interaction.click(identity("r1",800),650,160,0).intent().action());
        assertNull(interaction.click(identity("r1",800),650,160,1).intent());
        assertFalse(interaction.click(identity("r2",800),650,160,0).consumed());
    }

    private static QuestDetailsInteraction frame(boolean editing, boolean gameplay) {
        QuestDetailsInteraction interaction = new QuestDetailsInteraction();
        interaction.begin(identity("r1", 800), QUEST, editing, gameplay, PANEL,
                new UiRect(782, 20, 800, 36));
        return interaction;
    }

    private static QuestScreenFrameIdentity identity(String revision, int width) {
        return new QuestScreenFrameIdentity(BOOK, revision, false, width, 600);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("brnquest", path);
    }
}
