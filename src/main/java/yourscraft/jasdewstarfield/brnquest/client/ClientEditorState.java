package yourscraft.jasdewstarfield.brnquest.client;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import yourscraft.jasdewstarfield.brnquest.BrnQuestConstants;
import yourscraft.jasdewstarfield.brnquest.author.DraftOrigin;
import yourscraft.jasdewstarfield.brnquest.data.NativeBookJson;
import yourscraft.jasdewstarfield.brnquest.data.ChapterDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot;
import yourscraft.jasdewstarfield.brnquest.network.AuthoringNetwork;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Client-only projection of the server edit lease; it never replaces the active player snapshot. */
public final class ClientEditorState {
    private static final Gson GSON = new Gson();
    private static final int RENEW_INTERVAL_TICKS = 20 * 30;
    private static final ClientEditorState INSTANCE = new ClientEditorState();

    public enum Mode {
        VIEW, CATALOG_LOADING, OPENING, RECEIVING_DRAFT, EDITING, MUTATING, REVIEWING, SAVING, PUBLISHING,
        CLOSING, ERROR
    }

    public record CatalogEntry(ResourceLocation bookId, String title, String draftRevision, DraftOrigin origin) {}
    public record LeaseRequest(UUID sessionId, String draftRevision) {}

    private Mode mode = Mode.VIEW;
    private boolean allowed;
    private boolean live;
    private List<CatalogEntry> catalog = List.of();
    private String statusCode = "";
    private String statusMessage = "";
    private List<AuthoringNetwork.EditorDiagnosticWire> diagnostics = List.of();
    private UUID sessionId;
    private ResourceLocation bookId;
    private String baseRevision = "";
    private String draftRevision = "";
    private String savedRevision = "";
    private long leaseTicksAtResponse;
    private long ticksSinceLeaseResponse;
    private boolean renewPending;
    private int undoSteps;
    private int redoSteps;
    private ResourceLocation pendingBookId;
    private String[] chunks = new String[0];
    private int expectedBytes;
    private int receivedChunks;
    private QuestBookSnapshot draft;
    private boolean closeWhenOpened;
    private LeaseRequest immediateClose;
    private AuthoringNetwork.PublishReviewWire pendingPublishReview;
    private boolean recoverableConflict;

    private ClientEditorState() {}

    public static ClientEditorState get() { return INSTANCE; }

    public synchronized void beginCatalogRequest() {
        if (hasSession()) return;
        mode = Mode.CATALOG_LOADING;
        allowed = false;
        catalog = List.of();
        statusCode = "";
        statusMessage = "";
        diagnostics = List.of();
        closeWhenOpened = false;
        immediateClose = null;
    }

    public synchronized void acceptCatalog(String json) {
        // openCurrent sends a catalog refresh before OPEN. Catalog data must never
        // reset the handshake or an established lease back to the browsing mode.
        boolean editorFlowActive = mode == Mode.OPENING || hasSession();
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
            if (!editorFlowActive) {
                statusCode = safe(response.code());
                statusMessage = safe(response.message());
                diagnostics = List.of();
                mode = Mode.VIEW;
            }
        } catch (RuntimeException exception) {
            // The catalog is auxiliary to an already-started OPEN handshake. A malformed
            // refresh must not orphan the server lease by making its following reply stale.
            if (editorFlowActive) return;
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
        if (live) return Optional.empty();
        if (!editing() || !dirty() || busy() || recoverableConflict) return Optional.empty();
        mode = Mode.SAVING;
        statusCode = "DRAFT_SAVING";
        return Optional.of(new LeaseRequest(sessionId, draftRevision));
    }

    /** Starts the confirmed save/publish/deploy/reload pipeline; dirty drafts are saved by the server first. */
    public synchronized Optional<LeaseRequest> beginPublish() {
        if (live) return Optional.empty();
        if (!editing() || busy() || recoverableConflict) return Optional.empty();
        mode = Mode.PUBLISHING;
        statusCode = "DRAFT_PUBLISHING";
        return Optional.of(new LeaseRequest(sessionId, draftRevision));
    }

    public synchronized Optional<LeaseRequest> beginPublishReview() {
        if (live) return Optional.empty();
        if (mode != Mode.EDITING || !editing()) return Optional.empty();
        mode = Mode.REVIEWING;
        statusCode = "PUBLISH_REVIEWING";
        statusMessage = "";
        diagnostics = List.of();
        pendingPublishReview = null;
        return Optional.of(new LeaseRequest(sessionId, draftRevision));
    }

    public synchronized boolean beginMutation() {
        if (!editing() || busy() || recoverableConflict) return false;
        mode = Mode.MUTATING;
        statusCode = "DRAFT_MUTATING";
        statusMessage = "";
        diagnostics = List.of();
        return true;
    }

    public synchronized Optional<LeaseRequest> beginUndo() {
        return beginHistory(false);
    }

    public synchronized Optional<LeaseRequest> beginRedo() {
        return beginHistory(true);
    }

    private Optional<LeaseRequest> beginHistory(boolean redo) {
        if (mode != Mode.EDITING || !editing() || (redo ? redoSteps : undoSteps) <= 0) return Optional.empty();
        mode = Mode.MUTATING;
        statusCode = redo ? "DRAFT_REDOING" : "DRAFT_UNDOING";
        statusMessage = "";
        diagnostics = List.of();
        return Optional.of(new LeaseRequest(sessionId, draftRevision));
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
        String responseAction = safe(response.action());
        boolean success = "SUCCESS".equals(response.status()) || "NO_CHANGE".equals(response.status());
        if (success && ("UPDATE".equals(responseAction) || "MUTATE".equals(responseAction)
                || "PATCH".equals(responseAction)) && sessionId != null
                && !sessionId.equals(parseUuid(response.sessionId()))) {
            // Old-connection and superseded-session responses are harmless: they
            // neither replace the current draft nor force it into an error mode.
            return Optional.empty();
        }
        statusCode = safe(response.code());
        statusMessage = safe(response.message());
        diagnostics = response.diagnostics() == null ? List.of() : List.copyOf(response.diagnostics());
        if (!success) {
            if ("RENEW".equals(responseAction) && hasSession()
                    && "STALE_DRAFT_REVISION".equals(statusCode)) {
                // A heartbeat may already be travelling when a foreground mutation advances
                // the same lease. Its old revision is then expected, not an external edit
                // conflict; the mutation response remains the authoritative replacement.
                renewPending = false;
                return Optional.empty();
            }
            // Domain conflicts such as duplicate IDs belong to the open form and are
            // retryable. Only authority/revision divergence requires the full recovery UI.
            recoverableConflict = "CONFLICT".equals(response.status()) && hasSession() && draft != null
                    && requiresAuthoritativeRecovery(statusCode);
            fail(statusCode.isBlank() ? "EDITOR_SESSION_FAILED" : statusCode, statusMessage);
            return Optional.empty();
        }
        if ("OPEN".equals(response.action()) && mode != Mode.OPENING) {
            // An acknowledgement queued by a previous connection or an abandoned
            // open request must never resurrect its lease in the current client state.
            return Optional.empty();
        }
        return switch (responseAction) {
            case "OPEN" -> {
                live = "SESSION_LIVE_OPENED".equals(response.code());
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
            case "PATCH" -> {
                acceptPositionPatch(response);
                yield Optional.empty();
            }
            case "RECOVER" -> {
                ResourceLocation recoveredBook = ResourceLocation.tryParse(response.bookId());
                acceptOpened(response, recoveredBook != null && recoveredBook.equals(bookId));
                recoverableConflict = false;
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
            case "REVIEW" -> {
                acceptPublishReview(response);
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
        // Foreground operations already validate or renew the lease themselves. A
        // parallel heartbeat could otherwise return first and unlock their UI state.
        if (!editing() || busy() || renewPending
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

    /** Locks editing while one of the explicit conflict recovery choices is in flight. */
    public synchronized boolean beginRecovery() {
        if (mode != Mode.ERROR || !recoverableConflict || !hasSession() || draft == null) return false;
        mode = Mode.MUTATING;
        statusCode = "SESSION_RECOVERING";
        statusMessage = "";
        diagnostics = List.of();
        return true;
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
        recoverableConflict = false;
    }

    public synchronized Optional<LeaseRequest> pollImmediateClose() {
        LeaseRequest request = immediateClose;
        immediateClose = null;
        return Optional.ofNullable(request);
    }

    public synchronized Optional<AuthoringNetwork.PublishReviewWire> pollPublishReview() {
        AuthoringNetwork.PublishReviewWire review = pendingPublishReview;
        pendingPublishReview = null;
        return Optional.ofNullable(review);
    }

    public synchronized Mode mode() { return mode; }
    public synchronized boolean live() { return live; }
    /** Only acknowledged runtime definitions may drive item consumption or reward claims. */
    public synchronized boolean gameplayAllowed(String activeRevision) {
        return draft == null || live && !busy() && draft.revision().equals(activeRevision);
    }
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
    public synchronized List<AuthoringNetwork.EditorDiagnosticWire> diagnostics() { return diagnostics; }
    public synchronized int undoSteps() { return undoSteps; }
    public synchronized int redoSteps() { return redoSteps; }
    public synchronized boolean canUndo() { return mode == Mode.EDITING && undoSteps > 0; }
    public synchronized boolean canRedo() { return mode == Mode.EDITING && redoSteps > 0; }
    public synchronized boolean editing() { return hasSession() && draft != null; }
    public synchronized boolean hasLease() { return hasSession(); }
    public synchronized boolean recoverableConflict() { return mode == Mode.ERROR && recoverableConflict; }
    public synchronized boolean busy() {
        return mode == Mode.OPENING || mode == Mode.RECEIVING_DRAFT || mode == Mode.MUTATING
                || mode == Mode.REVIEWING || mode == Mode.SAVING || mode == Mode.PUBLISHING || mode == Mode.CLOSING;
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
        if (preserveVerifiedDraft && (sessionId == null || !sessionId.equals(decodedSession))) {
            fail("STALE_EDITOR_RESPONSE", "An older edit-session response was ignored");
            return;
        }
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
        undoSteps = response.undoSteps();
        redoSteps = response.redoSteps();
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
            // A heartbeat sent immediately before a foreground mutation may arrive
            // after that operation has advanced the authoritative draft revision and
            // finished transferring its chunks. The pending flag identifies that late
            // response even when the client has already returned to EDITING mode.
            // Heartbeats are advisory and never carry authored content. Any mismatched
            // response is therefore stale by definition; the next foreground request
            // still performs the authoritative session/revision check.
            renewPending = false;
            return;
        }
        savedRevision = safe(response.savedRevision());
        undoSteps = response.undoSteps();
        redoSteps = response.redoSteps();
        leaseTicksAtResponse = Math.max(0L, response.remainingTicks());
        ticksSinceLeaseResponse = 0L;
        renewPending = false;
        // Never let a late heartbeat response complete an unrelated save, mutation,
        // publish, or close operation from the client's point of view.
        if (!busy()) mode = draft == null ? Mode.RECEIVING_DRAFT : Mode.EDITING;
    }

    /** Applies a server-authored drag delta only when it reconstructs the advertised revision exactly. */
    private void acceptPositionPatch(AuthoringNetwork.SessionResponseWire response) {
        UUID decodedSession = parseUuid(response.sessionId());
        ResourceLocation decodedBook = ResourceLocation.tryParse(response.bookId());
        if (draft == null || sessionId == null || !sessionId.equals(decodedSession)
                || bookId == null || !bookId.equals(decodedBook)
                || response.positionPatch().isEmpty()
                || response.positionPatch().size() > BrnQuestConstants.MAX_QUESTS) {
            fail("INVALID_POSITION_PATCH", "The server returned an invalid position update");
            return;
        }
        Map<ResourceLocation, AuthoringNetwork.PositionWire> positions = new LinkedHashMap<>();
        for (AuthoringNetwork.PositionWire position : response.positionPatch()) {
            ResourceLocation questId = position == null ? null : ResourceLocation.tryParse(position.questId());
            if (questId == null || !Double.isFinite(position.x()) || !Double.isFinite(position.y())
                    || positions.putIfAbsent(questId, position) != null) {
                fail("INVALID_POSITION_PATCH", "The server returned malformed or duplicate positions");
                return;
            }
        }
        QuestBookDefinition current = draft.book();
        int[] applied = {0};
        List<ChapterDefinition> chapters = current.chapters().stream().map(chapter ->
                new ChapterDefinition(chapter.bookId(), chapter.id(), chapter.groupId(), chapter.title(),
                        chapter.icon(), chapter.order(), chapter.quests().stream().map(quest -> {
                    AuthoringNetwork.PositionWire position = positions.get(quest.id());
                    if (position == null) return quest;
                    applied[0]++;
                    return new QuestDefinition(quest.bookId(), quest.id(), quest.chapterId(), quest.title(),
                            quest.subtitle(), quest.description(), quest.icon(), position.x(), position.y(),
                            quest.dependencies(), quest.tasks(), quest.rewards(), quest.legacyId(),
                            quest.appearance(), quest.extensions());
                }).toList(), chapter.extensions())).toList();
        QuestBookSnapshot candidate = QuestBookSnapshot.of(new QuestBookDefinition(current.id(),
                current.schemaVersion(), current.title(), current.chapterGroups(), chapters, current.legacyIds(),
                current.localization(), current.extensions()));
        if (applied[0] != positions.size() || !candidate.revision().equals(response.draftRevision())) {
            fail("POSITION_PATCH_REVISION_MISMATCH", "The position update did not match the server revision");
            return;
        }
        draft = candidate;
        baseRevision = safe(response.baseRevision());
        draftRevision = response.draftRevision();
        savedRevision = safe(response.savedRevision());
        undoSteps = response.undoSteps();
        redoSteps = response.redoSteps();
        leaseTicksAtResponse = Math.max(0L, response.remainingTicks());
        ticksSinceLeaseResponse = 0L;
        renewPending = false;
        mode = Mode.EDITING;
        statusCode = dirty() ? "DRAFT_DIRTY" : "DRAFT_READY";
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
        undoSteps = response.undoSteps();
        redoSteps = response.redoSteps();
        leaseTicksAtResponse = Math.max(0L, response.remainingTicks());
        ticksSinceLeaseResponse = 0L;
        renewPending = false;
        mode = Mode.EDITING;
        statusCode = safe(response.code());
        statusMessage = safe(response.message());
    }

    private void acceptPublishReview(AuthoringNetwork.SessionResponseWire response) {
        UUID decodedSession = parseUuid(response.sessionId());
        ResourceLocation decodedBook = ResourceLocation.tryParse(response.bookId());
        if (sessionId == null || !sessionId.equals(decodedSession) || bookId == null || !bookId.equals(decodedBook)
                || !draftRevision.equals(response.draftRevision()) || response.review() == null
                || !draftRevision.equals(response.review().targetRevision())) {
            fail("STALE_PUBLISH_REVIEW", "The publish review no longer matches this edit session");
            return;
        }
        savedRevision = safe(response.savedRevision());
        undoSteps = response.undoSteps();
        redoSteps = response.redoSteps();
        leaseTicksAtResponse = Math.max(0L, response.remainingTicks());
        ticksSinceLeaseResponse = 0L;
        renewPending = false;
        pendingPublishReview = response.review();
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

    private static boolean requiresAuthoritativeRecovery(String code) {
        return "STALE_DRAFT_REVISION".equals(code) || "REVISION_CONFLICT".equals(code)
                || "DISK_DRAFT_CHANGED".equals(code) || "WORKSPACE_CHANGED".equals(code)
                || "LIVE_BOOK_CHANGED".equals(code);
    }

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
        live = false;
        sessionId = null;
        bookId = null;
        baseRevision = "";
        draftRevision = "";
        savedRevision = "";
        leaseTicksAtResponse = 0L;
        ticksSinceLeaseResponse = 0L;
        renewPending = false;
        undoSteps = 0;
        redoSteps = 0;
        chunks = new String[0];
        expectedBytes = 0;
        receivedChunks = 0;
        draft = null;
        diagnostics = List.of();
        pendingPublishReview = null;
        recoverableConflict = false;
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
