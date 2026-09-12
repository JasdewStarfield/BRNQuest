package yourscraft.jasdewstarfield.brnquest.client;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.BrnQuestConstants;
import yourscraft.jasdewstarfield.brnquest.data.NativeBookJson;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot;
import yourscraft.jasdewstarfield.brnquest.network.BrnQuestNetwork;
import yourscraft.jasdewstarfield.brnquest.progress.QuestStatus;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Client cache accepts a book only after manifest limits, order, size, and revision all match. */
public final class ClientQuestState {
    private static final ClientQuestState INSTANCE = new ClientQuestState();
    private static final Gson GSON = new Gson();
    private QuestBookSnapshot book;
    private String expectedRevision = "";
    private int expectedChunks;
    private int expectedBytes;
    private final Map<Integer, String> chunks = new HashMap<>();
    private Map<String, QuestStatus> statuses = Map.of();
    private Map<String, Long> taskProgress = Map.of();
    private Set<String> claimed = Set.of();
    private Set<String> visible = Set.of();
    private boolean visibilityAuthoritative;
    private Map<String, Integer> completionCycles = Map.of();
    private Map<String, Long> nextAvailable = Map.of();
    private final ClientActionWait pendingTaskSubmissions = new ClientActionWait();
    private final ClientActionWait rewardWait = new ClientActionWait();
    private final ClientActionWait completionWait = new ClientActionWait();

    public boolean beginQuestCompletion(String id) { return completionWait.begin(id, System.nanoTime()); }
    public boolean questCompletionPending(String id) { return completionWait.pending(id, System.nanoTime()); }

    public boolean beginRewardClaim(String id) { return rewardWait.begin(id, System.nanoTime()); }
    public boolean rewardClaimPending(String id) { return rewardWait.pending(id, System.nanoTime()); }
    public boolean rewardClaimTimedOut(String id) { return rewardWait.expired(id, System.nanoTime()); }
    public void finishRewardChoice(String id) { rewardWait.finish(id); }
    private ResourceLocation selected;
    private String bookSyncFailure = "";
    private String advertisedRevision = "";
    private String lastRejectedRevision = "";
    private String lastRejectedCode = "";
    private String lastAppliedRevision = "";
    private long receivedBytes;
    private int ignoredChunks;

    /** Immutable observation only; obtaining it never retries, clears a candidate or changes selection. */
    public record SyncHealth(String activeRevision, String advertisedRevision, String receivingRevision,
                             int receivedChunks, int expectedChunks, long receivedBytes, int expectedBytes,
                             String lastAppliedRevision, String lastRejectedRevision, String lastRejectedCode,
                             int ignoredChunks) {}

    public SyncHealth syncHealth() {
        return new SyncHealth(revision(), advertisedRevision, expectedRevision, chunks.size(), expectedChunks,
                receivedBytes, expectedBytes, lastAppliedRevision, lastRejectedRevision, lastRejectedCode, ignoredChunks);
    }

    /** A hello is an announcement, not proof that this client has applied that revision. */
    public void advertised(String revision) { advertisedRevision = revision == null ? "" : revision; }


    private ClientQuestState() {}
    public static ClientQuestState get() { return INSTANCE; }
    public Optional<QuestBookSnapshot> book() { return Optional.ofNullable(book); }
    public Map<String, QuestStatus> statuses() { return statuses; }
    public Map<String, Long> taskProgress() { return taskProgress; }
    public Set<String> claimed() { return claimed; }
    public boolean visible(ResourceLocation questId) { return !visibilityAuthoritative || visible.contains(questId.toString()); }
    public int completionCycles(ResourceLocation questId) { return completionCycles.getOrDefault(questId.toString(), 0); }
    public long nextAvailableAt(ResourceLocation questId) { return nextAvailable.getOrDefault(questId.toString(), 0L); }
    public ResourceLocation selected() { return selected; }
    public void selected(ResourceLocation selected) { this.selected = selected; }
    public String revision() { return book == null ? "" : book.revision(); }

    /** Returns false when the same task row already has an unanswered submission in flight. */
    public boolean beginTaskSubmission(String taskId) { return pendingTaskSubmissions.begin(taskId, System.nanoTime()); }

    public boolean isTaskSubmissionPending(String taskId) { return pendingTaskSubmissions.pending(taskId, System.nanoTime()); }

    public String bookSyncFailure() { return bookSyncFailure; }

    public boolean begin(String revision, int chunkCount, int decodedBytes) {
        if (revision == null || revision.isBlank() || chunkCount < 1
                || chunkCount > BrnQuestConstants.MAX_BOOK_CHUNKS || decodedBytes < 0
                || decodedBytes > BrnQuestConstants.MAX_BOOK_BYTES) {
            abortBookTransfer("INVALID_MANIFEST");
            lastRejectedRevision = revision == null ? "" : revision;
            return false;
        }
        expectedRevision = revision;
        expectedChunks = chunkCount;
        expectedBytes = decodedBytes;
        chunks.clear();
        receivedBytes = 0;
        bookSyncFailure = "";
        return true;
    }

    public boolean acceptChunk(String revision, int index, String data) {
        // A late chunk from a superseded transfer must not cancel the current manifest.
        if (expectedRevision.isBlank() || !expectedRevision.equals(revision)) {
            if (ignoredChunks < Integer.MAX_VALUE) ignoredChunks++;
            return false;
        }
        if (data == null || index < 0 || index >= expectedChunks
                || data.getBytes(StandardCharsets.UTF_8).length > BrnQuestConstants.MAX_BOOK_CHUNK_BYTES) {
            abortBookTransfer("INVALID_CHUNK");
            return false;
        }
        String previous = chunks.putIfAbsent(index, data);
        if (previous != null && !previous.equals(data)) {
            abortBookTransfer("CONFLICTING_CHUNK");
            return false;
        }
        if (previous == null) receivedBytes += data.getBytes(StandardCharsets.UTF_8).length;
        if (chunks.size() != expectedChunks) return false;
        try {
            StringBuilder json = new StringBuilder();
            for (int i = 0; i < expectedChunks; i++) {
                String chunk = chunks.get(i);
                if (chunk == null) throw new IllegalArgumentException("Missing book chunk");
                json.append(chunk);
            }
            if (json.toString().getBytes(StandardCharsets.UTF_8).length != expectedBytes) {
                abortBookTransfer("INVALID_BOOK");
                lastRejectedCode = "BYTE_COUNT_MISMATCH";
                return false;
            }
            QuestBookSnapshot candidate = QuestBookSnapshot.of(
                    NativeBookJson.decode(JsonParser.parseString(json.toString()).getAsJsonObject()));
            if (!candidate.revision().equals(expectedRevision)
                    || candidate.book().quests().size() > BrnQuestConstants.MAX_QUESTS) {
                boolean tooMany = candidate.book().quests().size() > BrnQuestConstants.MAX_QUESTS;
                abortBookTransfer("INVALID_BOOK");
                lastRejectedCode = tooMany ? "TOO_MANY_QUESTS" : "REVISION_MISMATCH";
                return false;
            }
            // The previous snapshot remains authoritative until the complete candidate passes every check.
            if (book == null || !book.revision().equals(candidate.revision())) {
                rewardWait.clear();
                completionWait.clear();
                pendingTaskSubmissions.clear();
            }
            book = candidate;
            lastAppliedRevision = candidate.revision();
            clearBookTransfer();
            bookSyncFailure = "";
            return true;
        } catch (RuntimeException exception) {
            abortBookTransfer("INVALID_BOOK");
            lastRejectedCode = "DECODE_FAILED";
            return false;
        }
    }

    public void bookSyncFailed(String code) {
        abortBookTransfer(code == null || code.isBlank() ? "UNKNOWN" : code);
    }

    private void abortBookTransfer(String code) {
        lastRejectedRevision = expectedRevision;
        lastRejectedCode = code;
        clearBookTransfer();
        bookSyncFailure = code;
    }

    private void clearBookTransfer() {
        expectedRevision = "";
        expectedChunks = 0;
        expectedBytes = 0;
        receivedBytes = 0;
        chunks.clear();
    }

    public void progress(String json) {
        if (json.getBytes(StandardCharsets.UTF_8).length > BrnQuestConstants.MAX_PROGRESS_BYTES) return;
        BrnQuestNetwork.ProgressWire wire = GSON.fromJson(json, BrnQuestNetwork.ProgressWire.class);
        // Progress already carries a revision. A delayed prior-book snapshot cannot replace live state or unlock input.
        if (wire == null || (book != null && !book.revision().equals(wire.revision()))) return;
        statuses = wire.quests() == null ? Map.of() : Map.copyOf(wire.quests());
        statuses.forEach((id, status) -> {
            if (status == QuestStatus.COMPLETED || status == QuestStatus.REWARD_CLAIMED) completionWait.finish(id);
        });
        taskProgress = wire.tasks() == null ? Map.of() : Map.copyOf(wire.tasks());
        claimed = wire.claimed() == null ? Set.of() : Set.copyOf(wire.claimed());
        // An unrelated progress refresh is not a receipt for an unanswered reward request.
        claimed.forEach(rewardWait::finish);
        visible = wire.visible() == null ? Set.of() : Set.copyOf(wire.visible());
        visibilityAuthoritative = wire.visible() != null;
        completionCycles = wire.cycles() == null ? Map.of() : Map.copyOf(wire.cycles());
        nextAvailable = wire.nextAvailable() == null ? Map.of() : Map.copyOf(wire.nextAvailable());
        // The server sends a progress response for every task submission, including rejected
        // attempts, so receipt is the acknowledgement that makes task rows clickable again.
        pendingTaskSubmissions.clear();
    }

    public Optional<ResourceLocation> trackedQuest() {
        return statuses.entrySet().stream().filter(e -> e.getValue() == QuestStatus.ACTIVE).map(e -> ResourceLocation.tryParse(e.getKey())).filter(Objects::nonNull).findFirst();
    }

    /** Connection-scoped caches must not expose the previous server's book or diagnostics after reconnecting. */
    public synchronized void disconnected() {
        rewardWait.clear();
        completionWait.clear();
        book = null;
        statuses = Map.of();
        taskProgress = Map.of();
        claimed = Set.of();
        visible = Set.of();
        visibilityAuthoritative = false;
        completionCycles = Map.of();
        nextAvailable = Map.of();
        pendingTaskSubmissions.clear();
        selected = null;
        advertisedRevision = "";
        lastRejectedRevision = "";
        lastRejectedCode = "";
        lastAppliedRevision = "";
        ignoredChunks = 0;
        bookSyncFailure = "";
        clearBookTransfer();
    }

    synchronized void resetForTest() { disconnected(); }
}
