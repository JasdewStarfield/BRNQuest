package yourscraft.jasdewstarfield.brnquest.progress;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Member counters must never merge, and every new cycle needs fresh personal completion. */
class AllMembersProgressTest {
    private static final UUID A = UUID.randomUUID(), B = UUID.randomUUID(), C = UUID.randomUUID();
    @Test void offlineMembersAndRosterChangesUseDurableIndividualCompletion() {
        var team = new PlayerProgress();
        team.addTaskProgress("task", 99); // Old shared counters cannot satisfy a new all-member quest.
        team.objectives(A).addTaskProgress("task", 3);
        team.objectives(A).status("quest", QuestStatus.COMPLETED);
        var restored = PlayerProgress.load(team.save());
        assertEquals(3, restored.objectives(A).taskProgress("task"));
        assertEquals(0, restored.objectives(B).taskProgress("task"));
        assertFalse(restored.allMembersCompleted("quest", Set.of(A, B)));
        assertTrue(restored.allMembersCompleted("quest", Set.of(A)));
        restored.objectives(B).status("quest", QuestStatus.COMPLETED);
        assertTrue(restored.allMembersCompleted("quest", Set.of(A, B)));
        assertFalse(restored.allMembersCompleted("quest", Set.of(A, B, C)));
        assertFalse(restored.allMembersCompleted("quest", Set.of()));
    }
    @Test void aliasesResetAndRepeatApplyToEveryMemberWithoutErasingOtherQuests() {
        var team = new PlayerProgress();
        for (var member : Set.of(A, B)) {
            team.objectives(member).addTaskProgress("old_task", 1);
            team.objectives(member).addTaskProgress("other", 7);
            team.objectives(member).status("old_quest", QuestStatus.COMPLETED);
        }
        assertTrue(team.migrateQuestId("old_quest", "quest"));
        assertTrue(team.migrateTaskId("old_task", "task"));
        team.claimMember(A, "reward");
        team.resetTask("quest", "task", QuestStatus.AVAILABLE);
        assertFalse(team.allMembersCompleted("quest", Set.of(A, B)));
        assertTrue(team.memberClaimed(A, "reward"));
        for (var member : Set.of(A, B)) {
            assertEquals(0, team.objectives(member).taskProgress("task"));
            assertEquals(7, team.objectives(member).taskProgress("other"));
            team.objectives(member).status("quest", QuestStatus.COMPLETED);
        }
        team.beginNextCycle("quest", List.of("task"), List.of("reward"), QuestStatus.AVAILABLE);
        assertFalse(team.allMembersCompleted("quest", Set.of(A, B)));
        assertFalse(team.memberClaimed(A, "reward"));
        team.objectives(A).status("quest", QuestStatus.COMPLETED);
        team.resetQuest("quest", List.of("task"), List.of("reward"));
        assertEquals(QuestStatus.LOCKED, team.objectives(A).status("quest"));
        assertEquals(7, team.objectives(A).taskProgress("other"));
    }
}
