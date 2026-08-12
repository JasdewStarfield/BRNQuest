package yourscraft.jasdewstarfield.brnquest.progress;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlayerProgressTest {
    @Test void saveRoundTripPreservesLedgerAndOrphans() {
        PlayerProgress progress = new PlayerProgress();
        progress.status("test:quest", QuestStatus.COMPLETED);
        progress.addTaskProgress("test:task", 4);
        progress.claim("test:reward");
        progress.orphan("test:old");
        progress.revision("ABC");
        PlayerProgress loaded = PlayerProgress.load(progress.save());
        assertEquals(QuestStatus.COMPLETED, loaded.status("test:quest"));
        assertEquals(4, loaded.taskProgress("test:task"));
        assertTrue(loaded.isClaimed("test:reward"));
        assertTrue(loaded.orphanedQuestIds().contains("test:old"));
        assertEquals("ABC", loaded.revision());
    }

    @Test void resetClearsOnlyQuestOwnedLedgerEntries() {
        PlayerProgress progress = new PlayerProgress();
        progress.status("test:quest", QuestStatus.REWARD_CLAIMED);
        progress.addTaskProgress("test:task", 1);
        progress.claim("test:reward");
        progress.claim("test:other_reward");
        progress.resetQuest("test:quest", List.of("test:task"), List.of("test:reward"));
        assertEquals(QuestStatus.LOCKED, progress.status("test:quest"));
        assertFalse(progress.isClaimed("test:reward"));
        assertTrue(progress.isClaimed("test:other_reward"));
    }
}
