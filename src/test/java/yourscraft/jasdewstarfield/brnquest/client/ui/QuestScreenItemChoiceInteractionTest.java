package yourscraft.jasdewstarfield.brnquest.client.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestScreenItemChoiceInteractionTest {
    @Test void candidateControlAlwaysOpensTheReadOnlyCandidateList() {
        // Inventory readiness must not reinterpret the explicit ellipsis affordance as submission.
        assertEquals(QuestScreen.ItemChoiceOpenMode.VIEW_CANDIDATES,
                QuestScreen.itemChoiceOpenMode(true, true, true, false));
        assertEquals(QuestScreen.ItemChoiceOpenMode.VIEW_CANDIDATES,
                QuestScreen.itemChoiceOpenMode(true, false, true, false));
    }

    @Test void actionableTaskRowStillOpensInventorySelectionWhenRequired() {
        assertEquals(QuestScreen.ItemChoiceOpenMode.SELECT_INVENTORY,
                QuestScreen.itemChoiceOpenMode(false, true, true, false));
    }

    @Test void lockedOrSubmittedTaskCannotOpenInventorySelection() {
        assertEquals(QuestScreen.ItemChoiceOpenMode.VIEW_CANDIDATES,
                QuestScreen.itemChoiceOpenMode(false, false, true, false));
        assertEquals(QuestScreen.ItemChoiceOpenMode.VIEW_CANDIDATES,
                QuestScreen.itemChoiceOpenMode(false, true, true, true));
    }
}
