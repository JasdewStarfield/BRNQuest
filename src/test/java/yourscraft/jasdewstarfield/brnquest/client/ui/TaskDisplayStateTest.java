package yourscraft.jasdewstarfield.brnquest.client.ui;

import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.progress.QuestStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskDisplayStateTest {
    @Test void itemPresentationsOnlySatisfyQuestWideChecksAfterAnIndividualReceipt() {
        // Inventory readiness and quest status cannot substitute for the shared item receipt rule.
        assertFalse(ClientTaskPresentationRegistry.itemObjectiveSubmitted(0));
        assertFalse(ClientTaskPresentationRegistry.itemObjectiveSubmitted(-1));
        assertTrue(ClientTaskPresentationRegistry.itemObjectiveSubmitted(1));
        assertTrue(ClientTaskPresentationRegistry.itemObjectiveSubmitted(Long.MAX_VALUE / 4));
    }

    @Test void authoritativeLedgerWinsOverLocalAndPendingState() {
        TaskDisplayState state = TaskDisplayState.resolve(
                QuestStatus.ACTIVE, 1, true, true, true, false);

        assertEquals(TaskDisplayState.SUBMITTED, state);
        assertTrue(state.confirmed());
        assertFalse(state.actionable());
    }

    @Test void completedQuestRemainsDistinctFromAnExplicitlySubmittedRow() {
        assertEquals(TaskDisplayState.HISTORICAL, TaskDisplayState.resolve(
                QuestStatus.COMPLETED, 0, false, true, true, false));
        assertEquals(TaskDisplayState.SUBMITTED, TaskDisplayState.resolve(
                QuestStatus.COMPLETED, 1, false, true, true, false));
    }

    @Test void historicalCompletionNeverInventsAReceiptOrAllowsResubmission() {
        // Covers newly added objectives and old auto-completed rows with no individual receipt.
        for (QuestStatus status : new QuestStatus[]{QuestStatus.COMPLETED, QuestStatus.REWARD_CLAIMED}) {
            for (boolean ready : new boolean[]{false, true}) {
                TaskDisplayState state = TaskDisplayState.resolve(status, 0, true, true, ready, false);
                assertEquals(TaskDisplayState.HISTORICAL, state);
                assertFalse(state.confirmed());
                assertFalse(state.actionable());
                assertEquals(TaskDisplayState.SUBMITTED,
                        TaskDisplayState.resolve(status, 1, false, true, ready, false));
            }
        }
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
