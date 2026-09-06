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
    private final Set<String> pendingTaskSubmissions = new HashSet<>();
    private ResourceLocation selected;
    private String bookSyncFailure = "";

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
    public boolean beginTaskSubmission(String taskId) { return pendingTaskSubmissions.add(taskId); }

    public boolean isTaskSubmissionPending(String taskId) { return pendingTaskSubmissions.contains(taskId); }

    public String bookSyncFailure() { return bookSyncFailure; }

    public boolean begin(String revision, int chunkCount, int decodedBytes) {
        if (revision == null || revision.isBlank() || chunkCount < 1
                || chunkCount > BrnQuestConstants.MAX_BOOK_CHUNKS || decodedBytes < 0
                || decodedBytes > BrnQuestConstants.MAX_BOOK_BYTES) {
            abortBookTransfer("INVALID_MANIFEST");
            return false;
        }
        expectedRevision = revision;
        expectedChunks = chunkCount;
        expectedBytes = decodedBytes;
        chunks.clear();
        bookSyncFailure = "";
        return true;
    }

    public boolean acceptChunk(String revision, int index, String data) {
        // A late chunk from a superseded transfer must not cancel the current manifest.
        if (expectedRevision.isBlank() || !expectedRevision.equals(revision)) return false;
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
        if (chunks.size() != expectedChunks) return false;
        try {
            StringBuilder json = new StringBuilder();
            for (int i = 0; i < expectedChunks; i++) {
                String chunk = chunks.get(i);
                if (chunk == null) throw new IllegalArgumentException("Missing book chunk");
                json.append(chunk);
            }
            if (json.toString().getBytes(StandardCharsets.UTF_8).length != expectedBytes) {
                throw new IllegalArgumentException("Book byte count does not match its manifest");
            }
            QuestBookSnapshot candidate = QuestBookSnapshot.of(
                    NativeBookJson.decode(JsonParser.parseString(json.toString()).getAsJsonObject()));
            if (!candidate.revision().equals(expectedRevision)
                    || candidate.book().quests().size() > BrnQuestConstants.MAX_QUESTS) {
                throw new IllegalArgumentException("Book identity or capacity does not match its manifest");
            }
            // The previous snapshot remains authoritative until the complete candidate passes every check.
            book = candidate;
            clearBookTransfer();
            bookSyncFailure = "";
            return true;
        } catch (RuntimeException exception) {
            abortBookTransfer("INVALID_BOOK");
            return false;
        }
    }

    public void bookSyncFailed(String code) {
        abortBookTransfer(code == null || code.isBlank() ? "UNKNOWN" : code);
    }

    private void abortBookTransfer(String code) {
        clearBookTransfer();
        bookSyncFailure = code;
    }

    private void clearBookTransfer() {
        expectedRevision = "";
        expectedChunks = 0;
        expectedBytes = 0;
        chunks.clear();
    }

    public void progress(String json) {
        if (json.getBytes(StandardCharsets.UTF_8).length > BrnQuestConstants.MAX_PROGRESS_BYTES) return;
        BrnQuestNetwork.ProgressWire wire = GSON.fromJson(json, BrnQuestNetwork.ProgressWire.class);
        statuses = wire.quests() == null ? Map.of() : Map.copyOf(wire.quests());
        taskProgress = wire.tasks() == null ? Map.of() : Map.copyOf(wire.tasks());
        claimed = wire.claimed() == null ? Set.of() : Set.copyOf(wire.claimed());
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

    synchronized void resetForTest() {
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
        bookSyncFailure = "";
        clearBookTransfer();
    }
}
