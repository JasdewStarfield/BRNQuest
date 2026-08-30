package yourscraft.jasdewstarfield.brnquest.progress;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ProgressAuditLogTest {
    @TempDir Path directory;

    @Test void appendsUtf8RecordsWithoutSplittingUserTextIntoFakeEntries() throws Exception {
        var intent = new AdminProgressService.Intent("target-uuid", "test:book", "revision", "test:quest",
                "test:task", AdminProgressAction.RESET_TASK);
        var before = new AdminProgressService.State(QuestStatus.REWARD_CLAIMED,
                Map.of("test:task", 1L), Set.of("test:reward"), 42, Map.of());
        var after = new AdminProgressService.State(QuestStatus.AVAILABLE,
                Map.of("test:task", 0L), Set.of("test:reward"), 0, Map.of());
        var entry = new ProgressAuditLog.Entry(1, "2026-08-30T00:00:00Z", "actor-uuid", "管理员\n另一行",
                intent, "目标玩家", "personal/target-uuid", before, after, "SUCCESS", "OK", "奖励账本保留");
        Path file = directory.resolve("reports/progress-audit.jsonl");
        ProgressAuditLog.append(file, entry);
        ProgressAuditLog.append(file, entry);
        var lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(2, lines.size());
        var json = JsonParser.parseString(lines.getFirst()).getAsJsonObject();
        assertEquals("管理员\n另一行", json.get("actorName").getAsString());
        assertEquals("目标玩家", json.get("targetName").getAsString());
        assertEquals("RESET_TASK", json.getAsJsonObject("intent").get("action").getAsString());
        assertEquals(1, json.getAsJsonObject("after").getAsJsonArray("claimed").size());
        assertEquals(0, json.getAsJsonObject("after").getAsJsonObject("tasks").get("test:task").getAsLong());
    }

    @Test void confirmationStateTracksDependenciesAndClaimLedgerNotOnlyQuestStatus() {
        var original = new AdminProgressService.State(QuestStatus.AVAILABLE, Map.of("task", 0L),
                Set.of(), 0, Map.of("parent", QuestStatus.COMPLETED));
        assertEquals(original, new AdminProgressService.State(QuestStatus.AVAILABLE, Map.of("task", 0L),
                Set.of(), 0, Map.of("parent", QuestStatus.COMPLETED)));
        assertNotEquals(original, new AdminProgressService.State(QuestStatus.AVAILABLE, Map.of("task", 1L),
                Set.of(), 0, Map.of("parent", QuestStatus.COMPLETED)));
        assertNotEquals(original, new AdminProgressService.State(QuestStatus.AVAILABLE, Map.of("task", 0L),
                Set.of("reward"), 0, Map.of("parent", QuestStatus.COMPLETED)));
        assertNotEquals(original, new AdminProgressService.State(QuestStatus.AVAILABLE, Map.of("task", 0L),
                Set.of(), 0, Map.of("parent", QuestStatus.LOCKED)));
    }
}
