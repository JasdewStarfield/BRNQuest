package yourscraft.jasdewstarfield.brnquest.progress;

import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.DependencyRequirement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestDependencyEvaluatorTest {
    @Test void reconciliationPromotesAndDemotesNonTerminalQuestStates() {
        assertEquals(QuestStatus.AVAILABLE,
                ProgressEngine.reconciledAvailability(QuestStatus.LOCKED, true));
        assertEquals(QuestStatus.LOCKED,
                ProgressEngine.reconciledAvailability(QuestStatus.AVAILABLE, false));
        assertEquals(QuestStatus.LOCKED,
                ProgressEngine.reconciledAvailability(QuestStatus.ACTIVE, false));
        assertEquals(QuestStatus.ACTIVE,
                ProgressEngine.reconciledAvailability(QuestStatus.ACTIVE, true));
        assertEquals(QuestStatus.COMPLETED,
                ProgressEngine.reconciledAvailability(QuestStatus.COMPLETED, false));
        assertEquals(QuestStatus.REWARD_CLAIMED,
                ProgressEngine.reconciledAvailability(QuestStatus.REWARD_CLAIMED, false));
    }

    @Test void coversAllFourAggregationModesAndExplicitThresholds() {
        assertTrue(QuestDependencyEvaluator.satisfied(DependencyRequirement.ALL_COMPLETED, 0, 2, 2, 2));
        assertFalse(QuestDependencyEvaluator.satisfied(DependencyRequirement.ALL_COMPLETED, 0, 2, 1, 2));
        assertTrue(QuestDependencyEvaluator.satisfied(DependencyRequirement.ONE_COMPLETED, 0, 3, 1, 1));
        assertTrue(QuestDependencyEvaluator.satisfied(DependencyRequirement.ALL_STARTED, 0, 2, 0, 2));
        assertTrue(QuestDependencyEvaluator.satisfied(DependencyRequirement.ONE_STARTED, 0, 3, 0, 1));
        assertFalse(QuestDependencyEvaluator.satisfied(DependencyRequirement.ONE_STARTED, 2, 3, 0, 1));
        assertTrue(QuestDependencyEvaluator.satisfied(DependencyRequirement.ONE_STARTED, 2, 3, 0, 2));
        assertTrue(QuestDependencyEvaluator.satisfied(DependencyRequirement.ALL_COMPLETED, 0, 0, 0, 0));
    }
}
