package yourscraft.jasdewstarfield.brnquest.author;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.BRNQuest;
import yourscraft.jasdewstarfield.brnquest.workspace.WorkspacePaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/** Append-only JSONL audit trail stored on the target server, separate from content revisions. */
public final class AuthorAuditLog {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Object WRITE_LOCK = new Object();

    private AuthorAuditLog() {}

    public static void record(ServerPlayer actor, String action, String object, String beforeRevision,
                              String afterRevision, AuthorOperationResult<?> result) {
        if (actor == null || actor.getServer() == null) return;
        Entry entry = new Entry(1, Instant.now().toString(), actor.getUUID().toString(), actor.getScoreboardName(),
                action, object == null ? "" : object, normalize(beforeRevision), normalize(afterRevision),
                result.status().name(), result.code(), result.message());
        try {
            append(WorkspacePaths.authorAudit(actor.getServer()), entry);
        } catch (IOException exception) {
            // Audit failure must be visible, but it must not retroactively corrupt or
            // roll back an already completed author transaction.
            BRNQuest.LOGGER.error("[BRNQuest] Failed to append author audit record", exception);
        }
    }

    static void append(Path file, Entry entry) throws IOException {
        synchronized (WRITE_LOCK) {
            Files.createDirectories(file.toAbsolutePath().normalize().getParent());
            Files.writeString(file, GSON.toJson(entry) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value;
    }

    record Entry(int formatVersion, String timestampUtc, String actorId, String actorName, String action,
                 String object, String beforeRevision, String afterRevision, String status, String code,
                 String message) {}
}
