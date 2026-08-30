package yourscraft.jasdewstarfield.brnquest.progress;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.commands.CommandSourceStack;
import yourscraft.jasdewstarfield.brnquest.BRNQuest;
import yourscraft.jasdewstarfield.brnquest.api.OperationResult;
import yourscraft.jasdewstarfield.brnquest.workspace.WorkspacePaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/** Append-only gameplay audit, deliberately separate from draft/content revisions and normal logs. */
public final class ProgressAuditLog {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private ProgressAuditLog() {}

    static void record(CommandSourceStack actor, AdminProgressService.Intent intent,
                       String targetName, String owner, AdminProgressService.State before,
                       AdminProgressService.State after, OperationResult result) {
        Entry entry = new Entry(1, Instant.now().toString(), AdminProgressService.actorId(actor),
                actor.getTextName(), intent, targetName, owner, before, after,
                result.status().name(), result.code(), result.message());
        try {
            append(WorkspacePaths.reports(actor.getServer()).resolve("progress-audit.jsonl"), entry);
        } catch (IOException exception) {
            // A failed audit append cannot undo an already delivered automatic reward.
            BRNQuest.LOGGER.error("[BRNQuest] Failed to append administrator progress audit", exception);
        }
    }

    static synchronized void append(Path file, Entry entry) throws IOException {
        Files.createDirectories(file.toAbsolutePath().normalize().getParent());
        Files.writeString(file, GSON.toJson(entry) + "\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    record Entry(int formatVersion, String timestampUtc, String actorId, String actorName,
                 AdminProgressService.Intent intent, String targetName, String owner,
                 AdminProgressService.State before, AdminProgressService.State after,
                 String status, String code, String message) {}
}
