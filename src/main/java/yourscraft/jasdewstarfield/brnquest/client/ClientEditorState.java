package yourscraft.jasdewstarfield.brnquest.client;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.BrnQuestConstants;
import yourscraft.jasdewstarfield.brnquest.author.DraftOrigin;
import yourscraft.jasdewstarfield.brnquest.data.NativeBookJson;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot;
import yourscraft.jasdewstarfield.brnquest.network.AuthoringNetwork;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** Client-only projection of the server edit lease; it never replaces the active player snapshot. */
public final class ClientEditorState {
    private static final Gson GSON = new Gson();
    private static final int RENEW_INTERVAL_TICKS = 20 * 30;
    private static final ClientEditorState INSTANCE = new ClientEditorState();

    public enum Mode {
        VIEW, CATALOG_LOADING, OPENING, RECEIVING_DRAFT, EDITING, MUTATING, SAVING, PUBLISHING, CLOSING, ERROR
    }

    public record CatalogEntry(ResourceLocation bookId, String title, String draftRevision, DraftOrigin origin) {}
    public record LeaseRequest(UUID sessionId, String draftRevision) {}

    private Mode mode = Mode.VIEW;
    private boolean allowed;
    private List<CatalogEntry> catalog = List.of();
    private String statusCode = "";
    private String statusMessage = "";
    private UUID sessionId;
    private ResourceLocation bookId;
    private String baseRevision = "";
    private String draftRevision = "";
    private String savedRevision = "";
    private long leaseTicksAtResponse;
    private long ticksSinceLeaseResponse;
    private boolean renewPending;
    private ResourceLocation pendingBookId;
    private String[] chunks = new String[0];
    private int expectedBytes;
    private int receivedChunks;
    private QuestBookSnapshot draft;
    private boolean closeWhenOpened;
    private LeaseRequest immediateClose;

    private ClientEditorState() {}

    public static ClientEditorState get() { return INSTANCE; }

    public synchronized void beginCatalogRequest() {
        if (hasSession()) return;
        mode = Mode.CATALOG_LOADING;
        allowed = false;
        catalog = List.of();
        statusCode = "";
        statusMessage = "";
        closeWhenOpened = false;
        immediateClose = null;
    }

    public synchronized void acceptCatalog(String json) {
        try {
            AuthoringNetwork.CatalogResponseWire response = GSON.fromJson(json,
                    AuthoringNetwork.CatalogResponseWire.class);
            if (response == null) throw new IllegalArgumentException("Missing catalog response");
            List<CatalogEntry> decoded = new ArrayList<>();
            if (response.entries() != null) {
                for (AuthoringNetwork.CatalogEntryWire entry : response.entries()) {
                    ResourceLocation id = ResourceLocation.tryParse(entry.bookId());
                    DraftOrigin origin = parseOrigin(entry.origin());
                    if (id != null && entry.draftRevision() != null && !entry.draftRevision().isBlank()) {
                        decoded.add(new CatalogEntry(id, entry.title() == null ? "" : entry.title(),
                                entry.draftRevision(), origin));
                    }
                    if (decoded.size() >= BrnQuestConstants.MAX_EDITOR_CATALOG_ENTRIES) break;
                }
            }
            catalog = List.copyOf(decoded);
            allowed = response.allowed();
            statusCode = safe(response.code());
            statusMessage = safe(response.message());
            mode = Mode.VIEW;
        } catch (RuntimeException exception) {
            fail("INVALID_EDITOR_CATALOG", "The server returned an invalid editor catalog");
            allowed = false;
            catalog = List.of();
        }
    }

    public synchronized boolean beginOpen(ResourceLocation targetBookId) {
        if (!allowed || targetBookId == null || hasSession() || !contains(targetBookId)) return false;
        return beginOpenRequest(targetBookId);
    }

    /** The server may create the active book's first draft before opening this request. */
    public synchronized boolean beginOpenCurrent(ResourceLocation targetBookId) {
        if (!allowed || targetBookId == null || hasSession()) return false;
        return beginOpenRequest(targetBookId);
    }

    private boolean beginOpenRequest(ResourceLocation targetBookId) {
        clearLease();
        bookId = targetBookId;
        mode = Mode.OPENING;
        statusCode = "SESSION_OPENING";
        statusMessage = "";
        return true;
    }

    /** Starts a close, optionally retaining a server book to open after acknowledgement. */
    public synchronized Optional<LeaseRequest> beginClose(ResourceLocation openAfterClose) {
        if (!hasSession() || busy()) return Optional.empty();
        pendingBookId = openAfterClose;
        mode = Mode.CLOSING;
        statusCode = openAfterClose == null ? "SESSION_CLOSING" : "SESSION_SWITCHING";
        return Optional.of(new LeaseRequest(sessionId, draftRevision));
    }

    public synchronized Optional<LeaseRequest> beginSave() {
        if (!editing() || !dirty() || busy()) return Optional.empty();
        mode = Mode.SAVING;
        statusCode = "DRAFT_SAVING";
        return Optional.of(new LeaseRequest(sessionId, draftRevision));
    }

    /** Starts the confirmed save/publish/deploy/reload pipeline; dirty drafts are saved by the server first. */
    public synchronized Optional<LeaseRequest> beginPublish() {
        if (!editing() || busy()) return Optional.empty();
        mode = Mode.PUBLISHING;
        statusCode = "DRAFT_PUBLISHING";
        return Optional.of(new LeaseRequest(sessionId, draftRevision));
    }

    public synchronized boolean beginMutation() {
        if (!editing() || busy()) return false;
        mode = Mode.MUTATING;
        statusCode = "DRAFT_MUTATING";
        return true;
    }

    /** Applies server session metadata and returns a queued book switch, if one became ready. */
    public synchronized Optional<ResourceLocation> acceptSession(String json) {
        AuthoringNetwork.SessionResponseWire response;
        try {
            response = GSON.fromJson(json, AuthoringNetwork.SessionResponseWire.class);
            if (response == null) throw new IllegalArgumentException("Missing session response");
        } catch (RuntimeException exception) {
            fail("INVALID_EDITOR_SESSION", "The server returned invalid edit-session metadata");
            return Optional.empty();
        }
        statusCode = safe(response.code());
        statusMessage = safe(response.message());
        boolean success = "SUCCESS".equals(response.status()) || "NO_CHANGE".equals(response.status());
        if (!success) {
            fail(statusCode.isBlank() ? "EDITOR_SESSION_FAILED" : statusCode, statusMessage);
            return Optional.empty();
        }
        return switch (safe(response.action())) {
            case "OPEN" -> {
                acceptOpened(response, false);
                yield Optional.empty();
            }
            case "UPDATE" -> {
                // Keep rendering the verified previous draft until every chunk of
                // the authoritative replacement has arrived and passed validation.
                acceptOpened(response, true);
                yield Optional.empty();
            }
            case "MUTATE" -> {
                acceptOpened(response, true);
                yield Optional.empty();
            }
            case "RENEW" -> {
                acceptRenewed(response);
                yield Optional.empty();
            }
            case "SAVE" -> {
                acceptSaved(response);
                yield Optional.empty();
            }
            case "PUBLISH" -> {
                acceptSaved(response);
                yield Optional.empty();
            }
            case "CLOSE" -> acceptClosed();
            default -> {
                fail("UNKNOWN_EDITOR_ACTION", "The server returned an unknown edit-session action");
                yield Optional.empty();
            }
        };
    }

    public synchronized boolean acceptDraftChunk(String rawSessionId, String revision, int index, String data) {
        if (mode != Mode.RECEIVING_DRAFT || sessionId == null || !sessionId.toString().equals(rawSessionId)
                || !draftRevision.equals(revision) || index < 0 || index >= chunks.length || data == null
                || data.getBytes(StandardCharsets.UTF_8).length > BrnQuestConstants.MAX_BOOK_CHUNK_BYTES) {
            return false;
        }
        if (chunks[index] == null) {
            chunks[index] = data;
            receivedChunks++;
        }
        if (receivedChunks != chunks.length) return true;
        try {
            String json = String.join("", chunks);
            if (json.getBytes(StandardCharsets.UTF_8).length != expectedBytes) {
                throw new IllegalArgumentException("Draft byte count does not match its manifest");
            }
            var book = NativeBookJson.decode(JsonParser.parseString(json).getAsJsonObject());
            QuestBookSnapshot candidate = QuestBookSnapshot.of(book);
            if (!book.id().equals(bookId) || !candidate.revision().equals(draftRevision)
                    || candidate.quests().size() > BrnQuestConstants.MAX_QUESTS) {
                throw new IllegalArgumentException("Draft identity or revision does not match its manifest");
            }
            draft = candidate;
            chunks = new String[0];
            mode = Mode.EDITING;
            statusCode = dirty() ? "DRAFT_DIRTY" : "DRAFT_READY";
            return true;
        } catch (RuntimeException exception) {
            fail("INVALID_EDITOR_DRAFT", "The synchronized draft could not be verified");
            return false;
        }
    }

    public synchronized void tick() {
        if (hasSession()) ticksSinceLeaseResponse++;
    }

    public synchronized Optional<LeaseRequest> pollRenewRequest() {
        if (!editing() || mode == Mode.RECEIVING_DRAFT || renewPending
                || ticksSinceLeaseResponse < RENEW_INTERVAL_TICKS) {
            return Optional.empty();
        }
        renewPending = true;
        return Optional.of(new LeaseRequest(sessionId, draftRevision));
    }

    public synchronized void abandonLocalSession() {
        pendingBookId = null;
        if (mode == Mode.OPENING && sessionId == null) {
            // The server may still grant the in-flight request. Close that lease as
            // soon as its identity arrives instead of leaving an invisible editor.
            closeWhenOpened = true;
            return;
        }
        clearLease();
        mode = Mode.VIEW;
    }

    /** A new connection must never inherit a draft or lease token from the previous server. */
    public synchronized void disconnected() {
        allowed = false;
        catalog = List.of();
        statusCode = "";
        statusMessage = "";
        pendingBookId = null;
        closeWhenOpened = false;
        immediateClose = null;
        clearLease();
        mode = Mode.VIEW;
    }

    public synchronized Optional<LeaseRequest> pollImmediateClose() {
        LeaseRequest request = immediateClose;
        immediateClose = null;
        return Optional.ofNullable(request);
    }

    public synchronized Mode mode() { return mode; }
    public synchronized boolean allowed() { return allowed; }
    public synchronized List<CatalogEntry> catalog() { return catalog; }

    /** Keeps the displayed book easy to reach while retaining backup-like drafts at the end. */
    public synchronized List<CatalogEntry> orderedCatalog(ResourceLocation displayedBookId) {
        return catalog.stream().sorted(Comparator
                .comparingInt((CatalogEntry entry) -> catalogRank(entry, displayedBookId))
                .thenComparing(entry -> catalogLabel(entry).toLowerCase(Locale.ROOT))
                .thenComparing(entry -> entry.bookId().toString()))
                .toList();
    }

    /** Applies the menu's title-or-ID filter after stable relevance ordering. */
    public synchronized List<CatalogEntry> filteredCatalog(ResourceLocation displayedBookId, String query) {
        String needle = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return orderedCatalog(displayedBookId).stream()
                .filter(entry -> needle.isEmpty() || catalogSearchText(entry).contains(needle))
                .toList();
    }

    public synchronized Optional<QuestBookSnapshot> draft() { return Optional.ofNullable(draft); }
    public synchronized ResourceLocation bookId() { return bookId; }
    public synchronized UUID sessionId() { return sessionId; }
    public synchronized String draftRevision() { return draftRevision; }
    public synchronized String statusCode() { return statusCode; }
    public synchronized String statusMessage() { return statusMessage; }
    public synchronized boolean editing() { return hasSession() && draft != null; }
    public synchronized boolean hasLease() { return hasSession(); }
    public synchronized boolean busy() {
        return mode == Mode.OPENING || mode == Mode.RECEIVING_DRAFT || mode == Mode.MUTATING
                || mode == Mode.SAVING || mode == Mode.PUBLISHING || mode == Mode.CLOSING;
    }
    public synchronized boolean dirty() {
        return !draftRevision.isBlank() && !draftRevision.equals(savedRevision);
    }
    public synchronized long remainingLeaseTicks() {
        return Math.max(0L, leaseTicksAtResponse - ticksSinceLeaseResponse);
    }

    synchronized void resetForTest() {
        disconnected();
    }

    private void acceptOpened(AuthoringNetwork.SessionResponseWire response, boolean preserveVerifiedDraft) {
        UUID decodedSession = parseUuid(response.sessionId());
        ResourceLocation decodedBook = ResourceLocation.tryParse(response.bookId());
        if (decodedSession == null || decodedBook == null || response.draftRevision() == null
                || response.draftRevision().isBlank() || response.chunks() < 1
                || response.decodedBytes() < 0 || response.decodedBytes() > BrnQuestConstants.MAX_BOOK_BYTES) {
            fail("INVALID_EDITOR_SESSION", "The server returned incomplete edit-session metadata");
            return;
        }
        sessionId = decodedSession;
        bookId = decodedBook;
        baseRevision = safe(response.baseRevision());
        draftRevision = response.draftRevision();
        savedRevision = safe(response.savedRevision());
        leaseTicksAtResponse = Math.max(0L, response.remainingTicks());
        ticksSinceLeaseResponse = 0L;
        renewPending = false;
        expectedBytes = response.decodedBytes();
        chunks = new String[response.chunks()];
        receivedChunks = 0;
        if (!preserveVerifiedDraft) draft = null;
        mode = Mode.RECEIVING_DRAFT;
        if (closeWhenOpened) {
            closeWhenOpened = false;
            immediateClose = new LeaseRequest(sessionId, draftRevision);
            mode = Mode.CLOSING;
        }
    }

    private void acceptRenewed(AuthoringNetwork.SessionResponseWire response) {
        UUID decodedSession = parseUuid(response.sessionId());
        if (sessionId == null || !sessionId.equals(decodedSession)
                || !draftRevision.equals(response.draftRevision())) {
            fail("STALE_EDITOR_RENEWAL", "The edit-session renewal no longer matches this draft");
            return;
        }
        savedRevision = safe(response.savedRevision());
        leaseTicksAtResponse = Math.max(0L, response.remainingTicks());
        ticksSinceLeaseResponse = 0L;
        renewPending = false;
        mode = draft == null ? Mode.RECEIVING_DRAFT : Mode.EDITING;
    }

    private void acceptSaved(AuthoringNetwork.SessionResponseWire response) {
        UUID decodedSession = parseUuid(response.sessionId());
        ResourceLocation decodedBook = ResourceLocation.tryParse(response.bookId());
        if (sessionId == null || !sessionId.equals(decodedSession) || bookId == null || !bookId.equals(decodedBook)
                || !draftRevision.equals(response.draftRevision())
                || !draftRevision.equals(response.savedRevision())) {
            fail("STALE_EDITOR_SAVE", "The saved draft no longer matches this edit session");
            return;
        }
        savedRevision = response.savedRevision();
        leaseTicksAtResponse = Math.max(0L, response.remainingTicks());
        ticksSinceLeaseResponse = 0L;
        renewPending = false;
        mode = Mode.EDITING;
        statusCode = safe(response.code());
        statusMessage = safe(response.message());
    }

    private Optional<ResourceLocation> acceptClosed() {
        ResourceLocation next = pendingBookId;
        pendingBookId = null;
        clearLease();
        mode = Mode.VIEW;
        if (next != null && beginOpen(next)) return Optional.of(next);
        return Optional.empty();
    }

    private boolean hasSession() { return sessionId != null; }

    private boolean contains(ResourceLocation target) {
        return catalog.stream().anyMatch(entry -> entry.bookId().equals(target));
    }

    private static int catalogRank(CatalogEntry entry, ResourceLocation displayedBookId) {
        if (entry.bookId().equals(displayedBookId)) return 0;
        return isBackupLike(entry) ? 2 : 1;
    }

    private static boolean isBackupLike(CatalogEntry entry) {
        String searchable = (entry.bookId() + " " + entry.title()).toLowerCase(Locale.ROOT);
        return searchable.contains("backup") || searchable.contains("备份");
    }

    private static String catalogLabel(CatalogEntry entry) {
        return entry.title().isBlank() ? entry.bookId().toString() : entry.title();
    }

    private static String catalogSearchText(CatalogEntry entry) {
        return (entry.bookId() + " " + entry.title()).toLowerCase(Locale.ROOT);
    }

    private void clearLease() {
        sessionId = null;
        bookId = null;
        baseRevision = "";
        draftRevision = "";
        savedRevision = "";
        leaseTicksAtResponse = 0L;
        ticksSinceLeaseResponse = 0L;
        renewPending = false;
        chunks = new String[0];
        expectedBytes = 0;
        receivedChunks = 0;
        draft = null;
    }

    private void fail(String code, String message) {
        mode = Mode.ERROR;
        statusCode = safe(code);
        statusMessage = safe(message);
        renewPending = false;
    }

    private static DraftOrigin parseOrigin(String value) {
        try {
            return DraftOrigin.valueOf(value);
        } catch (RuntimeException exception) {
            return DraftOrigin.UNKNOWN;
        }
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
