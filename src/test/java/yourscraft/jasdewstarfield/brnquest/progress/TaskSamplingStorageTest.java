package yourscraft.jasdewstarfield.brnquest.progress;

import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.task.TaskSamplingState;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** State must survive a zero-progress save, member departure and task rename, then clear at cycle/reset boundaries. */
class TaskSamplingStorageTest {
    private static final UUID A = UUID.randomUUID(), B = UUID.randomUUID();
    @Test void initializedZeroProgressAndMemberSamplesSurviveRoundTripAndRename() {
        var progress = new PlayerProgress();
        progress.prepareSamplingIdentity("q", "t", "jump/delta");
        progress.sampleState("t", A, new TaskSamplingState(Map.of("last", 80L, "contribution", 0L)));
        progress.samplePrepared("t", A, true);
        progress.suspendSample("t", A);
        progress.sampleState("t", B, new TaskSamplingState(Map.of("last", 20L, "contribution", 7L)));
        progress.objectives(B).addTaskProgress("t", 7);
        var restored = PlayerProgress.load(progress.save());
        assertEquals(80, restored.sampleState("t", A).values().get("last"));
        assertTrue(restored.migrateTaskId("t", "renamed"));
        assertEquals(80, restored.sampleState("renamed", A).values().get("last"));
        assertTrue(restored.samplePrepared("renamed", A));
        assertTrue(restored.sampleSuspended("renamed", A));
        assertEquals(7, restored.objectives(B).taskProgress("renamed"));
        assertTrue(restored.sampleState("t", A).values().isEmpty());
    }
    @Test void semanticChangeClearsAllMembersButSameKeyRetainsCounters() {
        var progress = new PlayerProgress();
        progress.prepareSamplingIdentity("q", "t", "jump/delta");
        progress.addTaskProgress("t", 7);
        progress.objectives(A).addTaskProgress("t", 4);
        progress.objectives(A).status("q", QuestStatus.COMPLETED);
        progress.sampleState("t", A, new TaskSamplingState(Map.of("last", 10L)));
        assertFalse(progress.prepareSamplingIdentity("q", "t", "jump/delta"));
        assertEquals(7, progress.taskProgress("t"));
        assertTrue(progress.prepareSamplingIdentity("q", "t", "distance/delta"));
        assertEquals(0, progress.taskProgress("t"));
        assertEquals(0, progress.objectives(A).taskProgress("t"));
        assertEquals(QuestStatus.AVAILABLE, progress.objectives(A).status("q"));
        assertTrue(progress.sampleState("t", A).values().isEmpty());
    }
    @Test void resetsAndNewCyclesClearBaselinesWithoutRemovingUnknownOtherTasks() {
        for (int action = 0; action < 3; action++) {
            var progress = new PlayerProgress();
            progress.prepareSamplingIdentity("q", "t", "jump/delta");
            progress.prepareSamplingIdentity("other", "foreign", "unknown/addon");
            progress.sampleState("t", A, new TaskSamplingState(Map.of("last", 80L)));
            progress.samplePrepared("t", A, true);
            progress.suspendSample("t", A);
            progress.sampleState("foreign", B, new TaskSamplingState(Map.of("last", 17L)));
            if (action == 0) progress.resetQuest("q", List.of("t"), List.of());
            if (action == 1) progress.beginNextCycle("q", List.of("t"), List.of(), QuestStatus.AVAILABLE);
            if (action == 2) progress.resetTask("q", "t", QuestStatus.AVAILABLE);
            assertTrue(progress.sampleState("t", A).values().isEmpty());
            assertFalse(progress.samplePrepared("t", A));
            assertFalse(progress.sampleSuspended("t", A));
            assertEquals(17, PlayerProgress.load(progress.save()).sampleState("foreign", B).values().get("last"));
        }
    }
    @Test void ownerSwitchAfterReloadSuspendsOldSamplesAndRosterDepartureKeepsTheContribution() {
        var owner = new yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerId(
                net.minecraft.resources.ResourceLocation.parse("test:team"), UUID.randomUUID());
        var next = new yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerId(owner.providerId(), UUID.randomUUID());
        var data = new QuestProgressData();
        data.activateSamplingOwner(A, owner);
        var ledger = data.get(owner);
        ledger.prepareSamplingIdentity("q", "t", "jump/delta");
        ledger.sampleState("t", A, new TaskSamplingState(Map.of("last", 80L, "contribution", 7L)));
        ledger.samplePrepared("t", A, true);
        ledger.addTaskProgress("t", 7);
        // Only primitive identifiers and counters cross the disk boundary; no server or player is needed here.
        var restored = QuestProgressData.load(data.save(new net.minecraft.nbt.CompoundTag(), null), null);
        restored.activateSamplingOwner(A, next);
        assertTrue(restored.get(owner).sampleSuspended("t", A));
        assertEquals(7, restored.get(owner).taskProgress("t"));
        restored.get(owner).resumeSample("t", A);
        restored.observe(owner, Set.of(A, B), yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerLifecycle.ACTIVE);
        restored.observe(owner, Set.of(B), yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerLifecycle.ACTIVE);
        assertTrue(restored.get(owner).sampleSuspended("t", A));
        assertEquals(7, restored.get(owner).sampleState("t", A).values().get("contribution"));
    }
}
