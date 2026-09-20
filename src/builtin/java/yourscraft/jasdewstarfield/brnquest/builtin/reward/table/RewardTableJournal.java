package yourscraft.jasdewstarfield.brnquest.builtin.reward.table;
import yourscraft.jasdewstarfield.brnquest.reward.*;
import yourscraft.jasdewstarfield.brnquest.task.*;
import yourscraft.jasdewstarfield.brnquest.builtin.reward.*;

import yourscraft.jasdewstarfield.brnquest.diagnostic.FileIoTrace;
import com.google.gson.Gson;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Forced, versioned attempt storage. Corrupt files and incomplete initial writes fail closed. */
public final class RewardTableJournal {
    public enum State { PREPARING, AWAITING_CHOICE, READY, EXECUTING, SUCCEEDED, BLOCKED, NEEDS_REVIEW, STALE }
    public enum LeafState { NOT_STARTED, STARTED, PENDING, SUCCEEDED, FAILED_NO_EFFECT, UNKNOWN, ACKNOWLEDGED }
    public record Leaf(String path, String occurrence, String type, Map<String,String> config,
                       Map<String,String> prepared, String adapterVersion, LeafState state, long startedAt, long completedAt, long updatedAt, String detail) {
        public Leaf { config = Map.copyOf(config); prepared = Map.copyOf(prepared); }
        public Leaf state(LeafState next, String message) {
            long now = System.currentTimeMillis();
            boolean finished = next == LeafState.SUCCEEDED || next == LeafState.ACKNOWLEDGED || next == LeafState.UNKNOWN || next == LeafState.FAILED_NO_EFFECT;
            return new Leaf(path, occurrence, type, config, prepared, adapterVersion, next,
                    next == LeafState.STARTED ? now : startedAt, finished ? now : completedAt, now, message);
        }
    }
    public record Attempt(int version, String key, String attemptId, String executor, String snapshot,
                          String resources, State state, List<Leaf> leaves, long updatedAt, String detail) {
        public Attempt { leaves = List.copyOf(leaves); }
        public Attempt state(State next, String message) {
            return new Attempt(version, key, attemptId, executor, snapshot, resources, next, leaves, System.currentTimeMillis(), message);
        }
        public Attempt leaf(int index, Leaf value) {
            List<Leaf> copy = new ArrayList<>(leaves); copy.set(index, value);
            return new Attempt(version, key, attemptId, executor, snapshot, resources, state, copy, System.currentTimeMillis(), detail);
        }
    }
    private static final Gson JSON = new Gson();
    private static final long MAX_STORAGE = 256L * 1024 * 1024;
    private final Path directory;
    /** Internal fault-injection seam for testing the persist-before-effect boundary deterministically. */
    @FunctionalInterface public interface BeforeWrite { void check(Path path) throws IOException; }
    private final BeforeWrite beforeWrite;
    private final BeforeWrite beforeReplace;
    public RewardTableJournal(Path directory) { this(directory, path -> {}); }
    public RewardTableJournal(Path directory, BeforeWrite beforeWrite) { this(directory, beforeWrite, path -> {}); }
    /** Package-local injection exercises transient replacement failures without touching a live save. */
    RewardTableJournal(Path directory, BeforeWrite beforeWrite, BeforeWrite beforeReplace) {
        this.directory = directory;
        this.beforeWrite = beforeWrite;
        this.beforeReplace = beforeReplace;
    }

    public Attempt read(String key) throws IOException {
        Path file = path(key);
        if (!Files.exists(file)) return null;
        return readFile(file, key);
    }
    private Attempt readFile(Path file, String key) throws IOException {
        try {
            if (Files.size(file) > 2 * 1024 * 1024) throw new IOException("Oversized table attempt");
            String text=FileIoTrace.readString(file, StandardCharsets.UTF_8);
            RewardTableTree.checkJsonDepth(text);
            Attempt value = JSON.fromJson(text, Attempt.class);
            if (value == null || (value.version != 1 && value.version != 2) || !value.key.equals(key) || value.state == null
                    || value.attemptId == null || value.executor == null || value.snapshot == null || value.resources == null
                    || value.leaves.size() > RewardTableTree.MAX_OCCURRENCES) throw new IOException("Invalid table attempt");
            UUID.fromString(value.attemptId); UUID.fromString(value.executor);
            Set<String> occurrences = new HashSet<>();
            for (Leaf leaf : value.leaves) if (leaf.state == null || leaf.path == null || leaf.type == null
                    || leaf.occurrence == null || leaf.adapterVersion == null || !occurrences.add(leaf.occurrence)) throw new IOException("Invalid leaf receipt");
            if (value.state == State.SUCCEEDED && value.leaves.stream().anyMatch(l -> l.state != LeafState.SUCCEEDED && l.state != LeafState.ACKNOWLEDGED))
                throw new IOException("Successful root contains an unconsumed leaf");
            // The snapshot is an escaped JSON string in the outer record and needs its own recursion guard.
            RewardTableTree.checkJsonDepth(value.snapshot);
            var snapshot = com.google.gson.JsonParser.parseString(value.snapshot).getAsJsonObject();
            if (value.version==2) RewardTablePlan.verify(value);
            if (value.version==1 && snapshot.has("mode") && snapshot.get("mode").getAsString().equals("choice")) {
                // A corrupt but parseable record must not turn a selection into a grant-all execution list.
                if (snapshot.has("brnquest.selected")) {
                    if (value.leaves.size() != 1 || !value.leaves.getFirst().path.equals("root/" + snapshot.get("brnquest.selected").getAsString()))
                        throw new IOException("Selected choice does not match its execution leaf");
                } else if (value.state == State.SUCCEEDED || value.leaves.stream().anyMatch(l -> l.state != LeafState.NOT_STARTED))
                    throw new IOException("Unconfirmed choice contains execution evidence");
            }
            return value;
        } catch (RuntimeException error) { throw new IOException("Unreadable table attempt; manual recovery required", error); }
    }
    public void begin(Attempt attempt) throws IOException {
        if (JSON.toJson(attempt).getBytes(StandardCharsets.UTF_8).length > 2 * 1024 * 1024) throw new IOException("Prepared attempt exceeds 2 MiB");
        FileIoTrace.createDirectories(directory);
        long bytes = 0; int unfinished = 0;
        try (var files = Files.list(directory)) {
            for (Path file : files.toList()) {
                bytes = Math.addExact(bytes, Files.size(file));
                if (bytes > MAX_STORAGE || Files.size(file) > 2 * 1024 * 1024) throw new IOException("Reward table storage capacity or record budget exceeded");
                if (file.toString().endsWith(".json")) {
                    // Count malformed files conservatively; never erase evidence to admit a new attempt.
                    try {
                        Attempt value = JSON.fromJson(FileIoTrace.readString(file, StandardCharsets.UTF_8), Attempt.class);
                        if (value == null || value.state != State.SUCCEEDED) unfinished++;
                    } catch (RuntimeException error) { unfinished++; }
                }
            }
        }
        if (unfinished >= 1024 || bytes + JSON.toJson(attempt).getBytes(StandardCharsets.UTF_8).length > MAX_STORAGE)
            throw new IOException("Reward table storage capacity reached; existing receipts retained");
        forced(path(attempt.key), JSON.toJson(attempt));
    }
    public void save(Attempt attempt) throws IOException {
        if (JSON.toJson(attempt).getBytes(StandardCharsets.UTF_8).length > 2 * 1024 * 1024) throw new IOException("Prepared attempt exceeds 2 MiB");
        Attempt current = read(attempt.key);
        if (current == null || !current.attemptId.equals(attempt.attemptId)) throw new IOException("Attempt changed or missing");
        Path target = path(attempt.key), temporary = directory.resolve(target.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            forced(temporary, JSON.toJson(attempt));
            replace(temporary, target);
        } finally { FileIoTrace.deleteIfExists(temporary); }
    }
    private void replace(Path temporary, Path target) throws IOException {
        // Windows readers can briefly deny replacement. Retry the same forced bytes, never the reward effect.
        // Keep atomic replacement mandatory; a permanent denial still stops execution with the old receipt intact.
        for (int retry = 0; ; retry++) {
            try {
                beforeReplace.check(target);
                FileIoTrace.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                if (retry > 0) yourscraft.jasdewstarfield.brnquest.BRNQuest.LOGGER.info(
                        "[BRNQuest/FILE_IO] recovered operation=reward-table-replace retries={} source={} target={}",
                        retry, temporary.toAbsolutePath().normalize(), target.toAbsolutePath().normalize());
                return;
            } catch (AccessDeniedException denied) {
                if (retry == 3) throw denied;
                try { Thread.sleep(10L << retry); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while replacing reward table receipt", denied);
                }
            }
        }
    }
    /** Acknowledgement records an explicit human decision and consumes only the named uncertain leaf. */
    public void acknowledge(String key, String attemptId, String occurrence, String actor) throws IOException {
        Attempt current = read(key);
        if (current == null || !current.attemptId.equals(attemptId)) throw new IOException("Attempt changed or missing");
        for (int i = 0; i < current.leaves.size(); i++) {
            Leaf leaf = current.leaves.get(i);
            if (!leaf.occurrence.equals(occurrence)) continue;
            if (leaf.state != LeafState.UNKNOWN && leaf.state != LeafState.FAILED_NO_EFFECT)
                throw new IOException("Only a stopped, reviewed leaf may be acknowledged");
            save(current.leaf(i, leaf.state(LeafState.ACKNOWLEDGED, "Acknowledged by " + actor + "; previous=" + leaf.state + ": " + leaf.detail))
                    .state(State.READY, "Administrator acknowledgement; no replay"));
            return;
        }
        throw new IOException("Unknown occurrence");
    }
    private Path path(String key) {
        try {
            return directory.resolve(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(key.getBytes(StandardCharsets.UTF_8))) + ".json");
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private void forced(Path path, String value) throws IOException {
        FileIoTrace.run("forced-write reward/table/RewardTableJournal.java", null, path, () -> {
            beforeWrite.check(path);
            try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)) {
                ByteBuffer bytes = StandardCharsets.UTF_8.encode(value);
                while (bytes.hasRemaining()) channel.write(bytes);
                channel.force(true);
            }
            return null;
        });
    }
}
