package yourscraft.jasdewstarfield.brnquest.reward;
import yourscraft.jasdewstarfield.brnquest.builtin.reward.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Pure tests isolate configuration and crash-recovery evidence from Minecraft command execution. */
class CommandRewardTest {
    @TempDir Path temporary;
    @Test void configNormalizesSlashAndOnlyExpandsSupportedTokens() {
        var config = CommandRewardConfig.decode(Map.of("command", " /tellraw {p} {\"text\":\"{x}, {y}, {z}\"} ")).getOrThrow();
        assertEquals("tellraw Dev {\"text\":\"-2, 64, 3\"}", config.expand("Dev", -2, 64, 3));
        assertEquals(2, config.permissionLevel());
        assertEquals("explicit", config.sourceMode());
        assertTrue(CommandRewardConfig.decode(Map.of("command", "say {team}")).error().isPresent());
        assertTrue(CommandRewardConfig.decode(Map.of("command", "say a\nsay b")).error().isPresent());
        assertTrue(CommandRewardConfig.decode(Map.of("command", "say ok", "permission_level", "5")).error().isPresent());
    }
    @Test void importedFeedbackRequiresAnAuthorResolutionWithoutDroppingTheSourceKey() {
        assertTrue(CommandRewardConfig.decode(Map.of("command", "say ok", "ftb.feedback_message", "missing.key")).error().isPresent());
        assertEquals("Done", CommandRewardConfig.decode(Map.of("command", "say ok", "ftb.feedback_message", "missing.key",
                "feedback", "Done", "source_mode", "player", "permission_level", "0")).getOrThrow().feedback());
    }
    @Test void intentSurvivesNewInstanceAndCannotBeReplayedOrAcknowledgedWithStaleToken() throws Exception {
        var journal = new CommandRewardJournal(temporary);
        var intent = journal.begin("owner/book/reward/1", "give @s minecraft:diamond");
        var reopened = new CommandRewardJournal(temporary);
        assertEquals("UNKNOWN", reopened.read(intent.key()).outcome().state());
        assertThrows(java.nio.file.FileAlreadyExistsException.class, () -> reopened.begin(intent.key(), "say duplicate"));
        assertThrows(java.io.IOException.class, () -> reopened.acknowledge(intent.key(), "stale", "admin"));
        reopened.outcome(intent.key(), new CommandRewardJournal.Outcome("REPORTED_FAILURE", 1, 0, "failed"));
        reopened.acknowledge(intent.key(), intent.attempt(), "admin");
        assertEquals("ACKNOWLEDGED", journal.read(intent.key()).outcome().state());
        assertTrue(journal.read(intent.key()).outcome().detail().contains("REPORTED_FAILURE"));
        assertNotEquals(intent.attempt(), reopened.begin("owner/book/reward/2", "say next cycle").attempt());
    }
    @Test void receiptKeysSeparateCyclesOwnersAndPersonalRecipientsButShareTeamRewards() {
        var owner = new yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerId(net.minecraft.resources.ResourceLocation.parse("brnquest:openpac"), java.util.UUID.randomUUID());
        var a = java.util.UUID.randomUUID(); var other = java.util.UUID.randomUUID();
        var book = net.minecraft.resources.ResourceLocation.parse("test:book");
        var id = net.minecraft.resources.ResourceLocation.parse("test:reward");
        var reward = new yourscraft.jasdewstarfield.brnquest.data.RewardDefinition(book, id, RewardTypes.COMMAND, Map.of("command", "say hi"), "manual", true);
        var quest = new yourscraft.jasdewstarfield.brnquest.data.QuestDefinition(book, id, id, "", "", "", "", 0, 0,
                java.util.List.of(), java.util.List.of(), java.util.List.of(reward), "");
        assertEquals(CommandRewardService.key(owner, a, quest, reward, 1), CommandRewardService.key(owner, other, quest, reward, 1));
        assertNotEquals(CommandRewardService.key(owner, a, quest, reward, 1), CommandRewardService.key(owner, a, quest, reward, 2));
        var personal = new yourscraft.jasdewstarfield.brnquest.data.RewardDefinition(book, id, RewardTypes.COMMAND, reward.config(), "manual", false);
        assertNotEquals(CommandRewardService.key(owner, a, quest, personal, 1), CommandRewardService.key(owner, other, quest, personal, 1));
        var otherOwner = new yourscraft.jasdewstarfield.brnquest.owner.ProgressOwnerId(owner.providerId(), java.util.UUID.randomUUID());
        assertNotEquals(CommandRewardService.key(owner, a, quest, reward, 1), CommandRewardService.key(otherOwner, a, quest, reward, 1));
    }

    @Test void corruptIntentFailsClosedInsteadOfPretendingNoAttemptExists() throws Exception {
        var journal = new CommandRewardJournal(temporary);
        journal.begin("key", "say once");
        Path file;
        try (var files = Files.list(temporary)) { file = files.findFirst().orElseThrow(); }
        Files.writeString(file, "{broken", java.nio.charset.StandardCharsets.UTF_8);
        assertThrows(java.io.IOException.class, () -> journal.read("key"));
    }
}
