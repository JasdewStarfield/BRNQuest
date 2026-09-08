package yourscraft.jasdewstarfield.brnquest.reward;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

/** Immutable forced intent files prevent automatic replay even when Minecraft's SavedData lags behind. */
public final class CommandRewardJournal {
    private static final Gson JSON = new Gson();
    private final Path directory;
    public record Intent(String key, String attempt, String command, long startedAt) {}
    public record Outcome(String state, int callbacks, int result, String detail) {}
    public record Receipt(Intent intent, Outcome outcome) {}
    public CommandRewardJournal(Path directory) { this.directory = directory; }

    public Receipt read(String key) throws IOException {
        Path intent = path(key, ".intent.json");
        if (!Files.exists(intent)) return null;
        try {
            Intent stored = JSON.fromJson(Files.readString(intent, StandardCharsets.UTF_8), Intent.class);
            if (stored == null || !key.equals(stored.key()) || stored.attempt() == null) throw new IOException("Invalid command intent");
            Path result = path(key, ".result.json");
            Outcome outcome = Files.exists(result) ? JSON.fromJson(Files.readString(result, StandardCharsets.UTF_8), Outcome.class)
                    : new Outcome("UNKNOWN", 0, 0, "Execution may not have started or may have completed; never auto-replay");
            if (outcome == null || outcome.state() == null) throw new IOException("Invalid command outcome");
            return new Receipt(stored, outcome);
        } catch (RuntimeException error) { throw new IOException("Unreadable command receipt", error); }
    }
    public Intent begin(String key, String command) throws IOException {
        Files.createDirectories(directory);
        Intent intent = new Intent(key, UUID.randomUUID().toString(), command, System.currentTimeMillis());
        // CREATE_NEW is the final duplicate guard; a torn file is treated as an error, never as absent.
        writeForced(path(key, ".intent.json"), JSON.toJson(intent));
        return intent;
    }
    public void outcome(String key, Outcome outcome) throws IOException {
        Path target = path(key, ".result.json");
        Path temporary = directory.resolve(target.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            writeForced(temporary, JSON.toJson(outcome));
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
    /** Administrative acknowledgement consumes an uncertain attempt; it never reruns its command. */
    public void acknowledge(String key, String attempt, String actor) throws IOException {
        Receipt receipt = read(key);
        if (receipt == null || !receipt.intent().attempt().equals(attempt)) throw new IOException("Receipt changed or missing");
        outcome(key, new Outcome("ACKNOWLEDGED", receipt.outcome().callbacks(), receipt.outcome().result(),
                "Acknowledged by " + actor + "; previous=" + receipt.outcome().state() + ": " + receipt.outcome().detail()));
    }
    private Path path(String key, String suffix) {
        try {
            String name = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));
            return directory.resolve(name + suffix);
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static void writeForced(Path path, String content) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)) {
            ByteBuffer bytes = StandardCharsets.UTF_8.encode(content);
            while (bytes.hasRemaining()) channel.write(bytes);
            channel.force(true);
        }
    }
}
