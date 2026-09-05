package yourscraft.jasdewstarfield.brnquest.progress;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import yourscraft.jasdewstarfield.brnquest.owner.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Regression coverage for durable identity, migration and per-recipient reward receipts. */
class SharedProgressStorageTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static ProgressOwnerId owner(String provider) {
        return new ProgressOwnerId(ResourceLocation.parse("brnquest:" + provider), PLAYER);
    }
    @Test void legacyPersonalAndTeamWithSameUuidNeverCollide() {
        PlayerProgress old = new PlayerProgress();
        old.addTaskProgress("test:task", 7);
        old.claim("test:reward");
        old.orphan("test:removed");
        CompoundTag legacy = new CompoundTag();
        CompoundTag players = new CompoundTag();
        players.put(PLAYER.toString(), old.save());
        legacy.put("players", players);
        var data = QuestProgressData.load(legacy, null);
        data.get(owner("openpac")).addTaskProgress("test:task", 2);
        data.get(owner("unknown_provider")).claim("test:foreign");
        var saved = data.save(new CompoundTag(), null);
        assertEquals(2, saved.getInt("schema_version"));
        assertFalse(saved.contains("players"));
        var restored = QuestProgressData.load(saved, null);
        assertEquals(7, restored.get(owner("personal")).taskProgress("test:task"));
        assertEquals(2, restored.get(owner("openpac")).taskProgress("test:task"));
        assertTrue(restored.get(owner("personal")).isClaimed("test:reward"));
        assertTrue(restored.get(owner("personal")).orphanedQuestIds().contains("test:removed"));
        assertTrue(restored.get(owner("unknown_provider")).isClaimed("test:foreign"));
    }
    @Test void archiveSurvivesRestartAndSameIdentityCanReactivateWithoutLosingReceipts() {
        var data = new QuestProgressData();
        var id = owner("openpac");
        data.get(id).claim("test:team");
        data.observe(id, Set.of(PLAYER, OTHER), ProgressOwnerLifecycle.ACTIVE);
        data.observe(id, Set.of(), ProgressOwnerLifecycle.UNAVAILABLE);
        assertTrue(data.archive(id).isEmpty());
        data.observe(id, Set.of(), ProgressOwnerLifecycle.ARCHIVED);
        var restored = QuestProgressData.load(data.save(new CompoundTag(), null), null);
        assertEquals(Set.of(PLAYER, OTHER), restored.archive(id).orElseThrow().members());
        restored.observe(id, Set.of(OTHER), ProgressOwnerLifecycle.ACTIVE);
        assertTrue(restored.archive(id).isEmpty());
        assertTrue(restored.get(id).isClaimed("test:team"));
    }
    @Test void cohortAndIndividualClaimsSurviveRenameRestartAndRepeatReset() {
        var progress = new PlayerProgress();
        progress.completionMembers("test:q", Set.of(PLAYER, OTHER));
        progress.claimMember(PLAYER, "test:reward");
        progress.claim("test:team");
        var restored = PlayerProgress.load(progress.save());
        assertEquals(Set.of(PLAYER, OTHER), restored.completionMembers("test:q"));
        assertEquals(Set.of("test:reward", "test:team"), restored.claimsFor(PLAYER));
        assertEquals(Set.of("test:team"), restored.claimsFor(OTHER));
        assertTrue(restored.migrateRewardId("test:reward", "test:renamed"));
        restored.status("test:q", QuestStatus.COMPLETED);
        restored.migrateQuestId("test:q", "test:new_q");
        assertEquals(Set.of(PLAYER, OTHER), restored.completionMembers("test:new_q"));
        restored.beginNextCycle("test:new_q", List.of(), List.of("test:renamed", "test:team"), QuestStatus.AVAILABLE);
        assertTrue(restored.claimsFor(PLAYER).isEmpty());
        assertTrue(restored.completionMembers("test:new_q").isEmpty());
    }
    @Test void sharedHudFocusIsPersonalAndPersistent() {
        var data = new QuestProgressData();
        data.tracked(PLAYER, "test:first");
        data.tracked(OTHER, "test:second");
        var restored = QuestProgressData.load(data.save(new CompoundTag(), null), null);
        assertEquals("test:first", restored.tracked(PLAYER));
        assertEquals("test:second", restored.tracked(OTHER));
    }
}
