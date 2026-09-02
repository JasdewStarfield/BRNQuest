package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.api.TaskView;
import yourscraft.jasdewstarfield.brnquest.progress.QuestStatus;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskDisplayStateTest {
    @Test void itemPresentationsOnlySatisfyQuestWideChecksAfterAnIndividualReceipt() {
        // Inventory readiness and quest status cannot substitute for the shared item receipt rule.
        TaskView ordinary = task(Map.of("count", "8"));
        assertFalse(ClientTaskPresentationRegistry.itemObjectiveSubmitted(ordinary, 0));
        assertTrue(ClientTaskPresentationRegistry.itemObjectiveSubmitted(ordinary, 1));

        TaskView crafting = task(Map.of("count", "8", "only_from_crafting", "true"));
        assertFalse(ClientTaskPresentationRegistry.itemObjectiveSubmitted(crafting, 4));
        assertTrue(ClientTaskPresentationRegistry.itemObjectiveSubmitted(crafting, 8));
    }

    @Test void authoritativeLedgerWinsOverLocalAndPendingState() {
        TaskDisplayState state = TaskDisplayState.resolve(
                QuestStatus.ACTIVE, true, true, true, true, false);

        assertEquals(TaskDisplayState.SUBMITTED, state);
        assertTrue(state.confirmed());
        assertFalse(state.actionable());
    }

    @Test void experienceReadinessMatchesTheConfiguredBalanceKind() {
        TaskView points = taskOfType("brnquest:xp", Map.of("value", "5", "points", "true"));
        assertFalse(ClientTaskPresentationRegistry.experienceAvailable(points, 4, 20));
        assertTrue(ClientTaskPresentationRegistry.experienceAvailable(points, 5, 0));

        TaskView levels = taskOfType("brnquest:xp", Map.of("value", "2", "points", "false"));
        assertFalse(ClientTaskPresentationRegistry.experienceAvailable(levels, 100, 1));
        assertTrue(ClientTaskPresentationRegistry.experienceAvailable(levels, 0, 2));
    }

    @Test void completedQuestRemainsDistinctFromAnExplicitlySubmittedRow() {
        assertEquals(TaskDisplayState.HISTORICAL, TaskDisplayState.resolve(
                QuestStatus.COMPLETED, false, false, true, true, false));
        assertEquals(TaskDisplayState.SUBMITTED, TaskDisplayState.resolve(
                QuestStatus.COMPLETED, true, false, true, true, false));
    }

    @Test void historicalCompletionNeverInventsAReceiptOrAllowsResubmission() {
        // Covers newly added objectives and old auto-completed rows with no individual receipt.
        for (QuestStatus status : new QuestStatus[]{QuestStatus.COMPLETED, QuestStatus.REWARD_CLAIMED}) {
            for (boolean ready : new boolean[]{false, true}) {
                TaskDisplayState state = TaskDisplayState.resolve(status, false, true, true, ready, false);
                assertEquals(TaskDisplayState.HISTORICAL, state);
                assertFalse(state.confirmed());
                assertFalse(state.actionable());
                assertEquals(TaskDisplayState.SUBMITTED,
                        TaskDisplayState.resolve(status, true, false, true, ready, false));
            }
        }
    }

    @Test void localReadinessIsYellowOnlyWhileTheQuestCanAcceptInput() {
        assertEquals(TaskDisplayState.READY, TaskDisplayState.resolve(
                QuestStatus.AVAILABLE, false, false, true, true, false));
        assertEquals(TaskDisplayState.PENDING, TaskDisplayState.resolve(
                QuestStatus.AVAILABLE, false, true, true, true, false));
        assertEquals(TaskDisplayState.UNMET, TaskDisplayState.resolve(
                QuestStatus.LOCKED, false, false, true, true, false));
        assertEquals(TaskDisplayState.UNMET, TaskDisplayState.resolve(
                QuestStatus.AVAILABLE, false, false, true, true, true));
    }

    private static TaskView task(Map<String, String> config) {
        return taskOfType("brnquest:item", config);
    }

    private static TaskView taskOfType(String typeId, Map<String, String> config) {
        return new TaskView(ResourceLocation.parse("test:book"), ResourceLocation.parse("test:task"),
                ResourceLocation.parse(typeId), config, false);
    }
}
