package yourscraft.jasdewstarfield.brnquest.client.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestTaskRowWidgetTest {
    @Test
    void passiveSatisfiedTaskCompletesQuestBeforeOtherFallbackActions() {
        assertEquals(QuestTaskRowWidget.Action.COMPLETE_QUEST,
                QuestTaskRowWidget.action(false, true, true, true));
    }

    @Test
    void interactiveConsumeTaskCanRequestInventorySelection() {
        assertEquals(QuestTaskRowWidget.Action.OPEN_TASK_INTERACTION,
                QuestTaskRowWidget.action(true, false, true, true));
    }

    @Test
    void ordinaryInteractiveTaskUsesDirectSubmissionIntent() {
        assertEquals(QuestTaskRowWidget.Action.SUBMIT_TASK,
                QuestTaskRowWidget.action(true, false, true, false));
    }
}
