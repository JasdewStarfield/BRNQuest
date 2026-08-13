package yourscraft.jasdewstarfield.brnquest.author;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AuthorAuditLogTest {
    @TempDir Path temporary;

    @Test void appendsOneUtf8JsonObjectPerOperationWithoutChangingRevisionData() throws Exception {
        Path audit = temporary.resolve("reports/author-audit.jsonl");
        AuthorAuditLog.Entry first = new AuthorAuditLog.Entry(1, "2026-08-14T00:00:00Z", "actor-1", "管理员",
                "draft_save", "test:book", "before", "after", "SUCCESS", "DRAFT_SAVED", "Saved");
        AuthorAuditLog.Entry second = new AuthorAuditLog.Entry(1, "2026-08-14T00:00:01Z", "actor-1", "管理员",
                "draft_publish", "test:book", "after", "after", "NO_CHANGE", "WORKSPACE_ALREADY_PUBLISHED", "No change");

        AuthorAuditLog.append(audit, first);
        AuthorAuditLog.append(audit, second);

        String content = Files.readString(audit, StandardCharsets.UTF_8);
        assertFalse(content.contains("\r"));
        var lines = content.lines().toList();
        assertEquals(2, lines.size());
        assertEquals("管理员", JsonParser.parseString(lines.getFirst()).getAsJsonObject()
                .get("actorName").getAsString());
        assertEquals("WORKSPACE_ALREADY_PUBLISHED", JsonParser.parseString(lines.getLast()).getAsJsonObject()
                .get("code").getAsString());
    }
}
