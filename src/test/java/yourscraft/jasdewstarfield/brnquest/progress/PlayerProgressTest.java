package yourscraft.jasdewstarfield.brnquest.progress;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.data.ChapterDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.data.RewardDefinition;
import yourscraft.jasdewstarfield.brnquest.data.TaskDefinition;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlayerProgressTest {
    @Test void emptyResetIsNoOpButPersistedAttemptSurvivesPartialReset() {
        var progress = new PlayerProgress();
        progress.resetQuest("q", List.of(), List.of());
        assertEquals("", progress.claimGeneration("q"));
        progress.rewardAttempted("q");
        progress = PlayerProgress.load(progress.save());
        progress.resetTask("q", "t", QuestStatus.AVAILABLE);
        progress.resetQuest("q", List.of("t"), List.of());
        String generation = progress.claimGeneration("q");
        assertFalse(generation.isBlank());
        progress.resetQuest("q", List.of("t"), List.of());
        assertEquals(generation, progress.claimGeneration("q"));
    }

    @Test void claimGenerationSurvivesSaveRenameAndPartialReset() {
        var progress = PlayerProgress.load(new net.minecraft.nbt.CompoundTag());
        assertEquals("", progress.claimGeneration("test:quest"), "old saves retain legacy receipt identity");
        progress.status("test:quest", QuestStatus.COMPLETED);
        progress.resetQuest("test:quest", List.of(), List.of());
        String first = progress.claimGeneration("test:quest");
        assertFalse(first.isBlank());
        var loaded = PlayerProgress.load(progress.save());
        assertEquals(first, loaded.claimGeneration("test:quest"));
        loaded.resetTask("test:quest", "test:task", QuestStatus.AVAILABLE);
        assertEquals(first, loaded.claimGeneration("test:quest"), "partial reset cannot reopen reward attempts");
        loaded.migrateQuestId("test:quest", "test:renamed");
        assertEquals(first, loaded.claimGeneration("test:renamed"));
        loaded.beginNextCycle("test:renamed", List.of(), List.of(), QuestStatus.AVAILABLE);
        assertEquals(first, loaded.claimGeneration("test:renamed"));
        loaded.rewardAttempted("test:renamed");
        loaded.resetQuest("test:renamed", List.of(), List.of());
        assertNotEquals(first, loaded.claimGeneration("test:renamed"));
    }

    @Test void taskResetReopensQuestButPreservesOtherTasksAndClaimsAcrossSave() {
        PlayerProgress progress = new PlayerProgress();
        progress.status("test:quest", QuestStatus.REWARD_CLAIMED);
        progress.completedAt("test:quest", 42);
        progress.addTaskProgress("test:first", 1);
        progress.addTaskProgress("test:second", 1);
        progress.claim("test:reward");
        progress.completedCycle("test:quest", 42, 84);
        progress.resetTask("test:quest", "test:first", QuestStatus.AVAILABLE);
        PlayerProgress loaded = PlayerProgress.load(progress.save());
        assertEquals(QuestStatus.AVAILABLE, loaded.status("test:quest"));
        assertEquals(0, loaded.completedAt("test:quest"));
        assertEquals(0, loaded.taskProgress("test:first"));
        assertEquals(1, loaded.taskProgress("test:second"));
        assertTrue(loaded.isClaimed("test:reward"));
        assertEquals(0, loaded.completionCycles("test:quest"));
        assertEquals(0, loaded.nextAvailableAt("test:quest"));
        loaded.resetTask("test:quest", "test:first", QuestStatus.LOCKED);
        assertEquals(QuestStatus.LOCKED, loaded.status("test:quest"));
        assertTrue(loaded.isClaimed("test:reward"));
    }

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

    @Test void repeatCyclePersistsAndStartsWithoutReopeningOldClaims() {
        PlayerProgress progress = new PlayerProgress();
        progress.status("test:quest", QuestStatus.REWARD_CLAIMED);
        progress.addTaskProgress("test:task", 4);
        progress.claim("test:reward");
        progress.completedCycle("test:quest", 100L, 200L);

        PlayerProgress loaded = PlayerProgress.load(progress.save());
        assertEquals(1, loaded.completionCycles("test:quest"));
        assertEquals(200L, loaded.nextAvailableAt("test:quest"));
        assertTrue(loaded.isClaimed("test:reward"));

        loaded.beginNextCycle("test:quest", List.of("test:task"), List.of("test:reward"), QuestStatus.AVAILABLE);
        assertEquals(QuestStatus.AVAILABLE, loaded.status("test:quest"));
        assertEquals(0L, loaded.taskProgress("test:task"));
        assertFalse(loaded.isClaimed("test:reward"));
        assertEquals(1, loaded.completionCycles("test:quest"));
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

    @Test void canonicalRenameMigratesQuestStateButKeepsStableNestedLedgers() {
        PlayerProgress progress = new PlayerProgress();
        progress.status("test:old", QuestStatus.COMPLETED);
        progress.completedAt("test:old", 42L);
        progress.addTaskProgress("test:task", 3L);
        progress.claim("test:reward");

        assertTrue(progress.migrateQuestId("test:old", "test:new"));
        assertEquals(QuestStatus.LOCKED, progress.status("test:old"));
        assertEquals(QuestStatus.COMPLETED, progress.status("test:new"));
        assertEquals(42L, progress.completedAt("test:new"));
        assertEquals(3L, progress.taskProgress("test:task"));
        assertTrue(progress.isClaimed("test:reward"));
    }

    @Test void explicitTypedIdRenamesMigrateAndMergeNestedLedgers() {
        PlayerProgress progress = new PlayerProgress();
        progress.addTaskProgress("test:old_task", 7L);
        progress.addTaskProgress("test:new_task", 3L);
        progress.claim("test:old_reward");

        assertTrue(progress.migrateTaskId("test:old_task", "test:new_task"));
        assertEquals(0L, progress.taskProgress("test:old_task"));
        assertEquals(7L, progress.taskProgress("test:new_task"));
        assertTrue(progress.migrateRewardId("test:old_reward", "test:new_reward"));
        assertFalse(progress.isClaimed("test:old_reward"));
        assertTrue(progress.isClaimed("test:new_reward"));
    }

    @Test void reconcileSkipsAliasesWhoseRetiredSourceIsLiveAgain() {
        PlayerProgress progress = new PlayerProgress();
        progress.status("test:old_quest", QuestStatus.COMPLETED);
        progress.addTaskProgress("test:old_task", 1L);
        progress.claim("test:old_reward");

        ProgressEngine.reconcileLegacyIds(bookWithTypedAliases(true), progress);

        assertEquals(QuestStatus.COMPLETED, progress.status("test:old_quest"));
        assertEquals(QuestStatus.LOCKED, progress.status("test:new_quest"));
        assertEquals(1L, progress.taskProgress("test:old_task"));
        assertEquals(0L, progress.taskProgress("test:new_task"));
        assertTrue(progress.isClaimed("test:old_reward"));
        assertFalse(progress.isClaimed("test:new_reward"));
    }

    @Test void reconcileMigratesAliasesWhenTheirSourceIsRetired() {
        PlayerProgress progress = new PlayerProgress();
        progress.status("test:old_quest", QuestStatus.COMPLETED);
        progress.addTaskProgress("test:old_task", 1L);
        progress.claim("test:old_reward");

        ProgressEngine.reconcileLegacyIds(bookWithTypedAliases(false), progress);

        assertEquals(QuestStatus.LOCKED, progress.status("test:old_quest"));
        assertEquals(QuestStatus.COMPLETED, progress.status("test:new_quest"));
        assertEquals(0L, progress.taskProgress("test:old_task"));
        assertEquals(1L, progress.taskProgress("test:new_task"));
        assertFalse(progress.isClaimed("test:old_reward"));
        assertTrue(progress.isClaimed("test:new_reward"));
    }

    private static QuestBookDefinition bookWithTypedAliases(boolean includeRetiredSources) {
        ResourceLocation bookId = ResourceLocation.parse("test:book");
        ResourceLocation chapterId = ResourceLocation.parse("test:chapter");
        List<TaskDefinition> tasks = new java.util.ArrayList<>();
        List<RewardDefinition> rewards = new java.util.ArrayList<>();
        tasks.add(new TaskDefinition(bookId, ResourceLocation.parse("test:new_task"),
                ResourceLocation.parse("test:type"), Map.of(), false));
        rewards.add(new RewardDefinition(bookId, ResourceLocation.parse("test:new_reward"),
                ResourceLocation.parse("test:type"), Map.of(), "manual", false));
        if (includeRetiredSources) {
            tasks.add(new TaskDefinition(bookId, ResourceLocation.parse("test:old_task"),
                    ResourceLocation.parse("test:type"), Map.of(), false));
            rewards.add(new RewardDefinition(bookId, ResourceLocation.parse("test:old_reward"),
                    ResourceLocation.parse("test:type"), Map.of(), "manual", false));
        }
        QuestDefinition quest = new QuestDefinition(bookId, ResourceLocation.parse("test:new_quest"), chapterId,
                "Quest", "", "", "", 0.0, 0.0, List.of(), tasks, rewards, "");
        List<QuestDefinition> quests = new java.util.ArrayList<>();
        quests.add(quest);
        if (includeRetiredSources) {
            quests.add(new QuestDefinition(bookId, ResourceLocation.parse("test:old_quest"), chapterId,
                    "Old quest", "", "", "", 1.0, 0.0, List.of(), List.of(), List.of(), ""));
        }
        ChapterDefinition chapter = new ChapterDefinition(bookId, chapterId, ResourceLocation.parse("test:group"),
                "Chapter", "", 0, quests);
        return new QuestBookDefinition(bookId, 1, "Book", List.of(), List.of(chapter), Map.of(
                "test:old_quest", ResourceLocation.parse("test:new_quest"),
                "@task:test:old_task", ResourceLocation.parse("test:new_task"),
                "@reward:test:old_reward", ResourceLocation.parse("test:new_reward")));
    }
}
