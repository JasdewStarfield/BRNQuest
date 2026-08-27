package yourscraft.jasdewstarfield.brnquest.client.ui;

import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.progress.QuestStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskDisplayStateTest {
    @Test void authoritativeLedgerWinsOverLocalAndPendingState() {
        TaskDisplayState state = TaskDisplayState.resolve(
                QuestStatus.ACTIVE, 1, true, true, true, false);

        assertEquals(TaskDisplayState.SUBMITTED, state);
        assertTrue(state.confirmed());
        assertFalse(state.actionable());
    }

    @Test void completedQuestRemainsDistinctFromAnExplicitlySubmittedRow() {
        assertEquals(TaskDisplayState.COMPLETED, TaskDisplayState.resolve(
                QuestStatus.COMPLETED, 0, false, true, true, false));
        assertEquals(TaskDisplayState.SUBMITTED, TaskDisplayState.resolve(
                QuestStatus.COMPLETED, 1, false, true, true, false));
    }

    @Test void localReadinessIsYellowOnlyWhileTheQuestCanAcceptInput() {
        assertEquals(TaskDisplayState.READY, TaskDisplayState.resolve(
                QuestStatus.AVAILABLE, 0, false, true, true, false));
        assertEquals(TaskDisplayState.PENDING, TaskDisplayState.resolve(
                QuestStatus.AVAILABLE, 0, true, true, true, false));
        assertEquals(TaskDisplayState.UNMET, TaskDisplayState.resolve(
                QuestStatus.LOCKED, 0, false, true, true, false));
        assertEquals(TaskDisplayState.UNMET, TaskDisplayState.resolve(
                QuestStatus.AVAILABLE, 0, false, true, true, true));
    }
}
