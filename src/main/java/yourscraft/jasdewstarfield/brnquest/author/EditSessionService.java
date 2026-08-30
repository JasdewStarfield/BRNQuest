package yourscraft.jasdewstarfield.brnquest.author;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Function;

/**
 * Server-scoped single-writer leases for local or remote administrators.
 * Draft content never lives on the connecting client's filesystem.
 */
public final class EditSessionService {
    /** Thirty minutes leaves command authors time to inspect IDs while stage-5 clients still renew proactively. */
    public static final long DEFAULT_IDLE_TIMEOUT_TICKS = 20L * 60L * 30L;
    static final int MAX_HISTORY_STEPS = 64;
    private static final EditSessionService INSTANCE = new EditSessionService();

    // Weak server keys keep integrated-server restarts and GameTest servers isolated
    // without retaining a stopped MinecraftServer for the rest of the client process.
    private final Map<Object, ServerSessions> servers = new WeakHashMap<>();

    // Package visibility lets deterministic unit tests use an isolated lease table.
    EditSessionService() {}

    public static EditSessionService get() {
        return INSTANCE;
    }

    /** Lightweight sessions start from the active book, never from an unfinished saved draft. */
    public synchronized AuthorOperationResult<EditSessionHandle> openLive(ServerPlayer player, ResourceLocation bookId) {
        MinecraftServer server = connectedServer(player);
        if (server == null) return notConnected();
        if (!isAdministrator(player)) return forbidden();
        pruneDisconnected(server);
        var active = yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager.get().active().orElse(null);
        if (active == null || !active.book().id().equals(bookId)) {
            return AuthorOperationResult.failure(AuthorOperationResult.Status.CONFLICT, "LIVE_BOOK_CHANGED", "Displayed book is no longer active");
        }
        var opened = openAuthorized(server, player.getUUID(), player.getScoreboardName(),
                DraftSnapshot.from(active.book(), DraftOrigin.ACTIVE, active.revision()),
                server.getTickCount(), DEFAULT_IDLE_TIMEOUT_TICKS);
        if (!opened.success()) return opened;
        Lease lease = lease(player.getServer(), opened.value().sessionId());
        if (!lease.live && opened.status() == AuthorOperationResult.Status.NO_CHANGE) {
            return AuthorOperationResult.failure(AuthorOperationResult.Status.CONFLICT,
                    "BOOK_ALREADY_EDITED", "Close the existing draft session before live editing");
        }
        lease.live = true;
        return AuthorOperationResult.success("SESSION_LIVE_OPENED", "Live editor opened", lease.handle());
    }

    public synchronized AuthorOperationResult<EditSessionHandle> open(ServerPlayer player, DraftSnapshot draft) {
        MinecraftServer server = connectedServer(player);
        if (server == null) return notConnected();
        if (!isAdministrator(player)) return forbidden();
        pruneDisconnected(server);
        var opened = openAuthorized(server, player.getUUID(), player.getScoreboardName(), draft,
                server.getTickCount(), DEFAULT_IDLE_TIMEOUT_TICKS);
        if (opened.success() && lease(server, opened.value().sessionId()).live) {
            return AuthorOperationResult.failure(AuthorOperationResult.Status.CONFLICT,
                    "BOOK_ALREADY_EDITED", "Close live editing before opening an advanced draft");
        }
        return opened;
    }

    public synchronized AuthorOperationResult<EditSessionHandle> renew(ServerPlayer player, UUID sessionId,
                                                                        String expectedDraftRevision) {
        MinecraftServer server = connectedServer(player);
        if (server == null) return notConnected();
        if (!isAdministrator(player)) {
            releasePlayer(server, player.getUUID());
            return forbidden();
        }
        pruneDisconnected(server);
        return renewAuthorized(server, player.getUUID(), sessionId, expectedDraftRevision,
                server.getTickCount(), DEFAULT_IDLE_TIMEOUT_TICKS);
    }

    public synchronized AuthorOperationResult<EditSessionView> close(ServerPlayer player, UUID sessionId,
                                                                      String expectedDraftRevision) {
        MinecraftServer server = connectedServer(player);
        if (server == null) return notConnected();
        if (!isAdministrator(player)) {
            releasePlayer(server, player.getUUID());
            return forbidden();
        }
        return closeAuthorized(server, player.getUUID(), sessionId, expectedDraftRevision, server.getTickCount());
    }

    /** Other administrators may inspect occupancy, but never receive the owner's session token. */
    public synchronized AuthorOperationResult<EditSessionView> inspect(ServerPlayer player, ResourceLocation bookId) {
        MinecraftServer server = connectedServer(player);
        if (server == null) return notConnected();
        if (!isAdministrator(player)) return forbidden();
        pruneDisconnected(server);
        return inspectAuthorized(server, bookId, server.getTickCount());
    }

    /** Returns the authoritative in-memory draft only to the lease owner. */
    public synchronized AuthorOperationResult<DraftSnapshot> snapshot(ServerPlayer player, UUID sessionId,
                                                                       ResourceLocation bookId,
                                                                       String expectedDraftRevision) {
        MinecraftServer server = connectedServer(player);
        if (server == null) return notConnected();
        if (!isAdministrator(player)) {
            releasePlayer(server, player.getUUID());
            return forbidden();
        }
        pruneDisconnected(server);
        Lease lease = lease(server, sessionId);
        AuthorOperationResult<DraftSnapshot> denied = denyLease(lease, player.getUUID(), bookId,
                expectedDraftRevision, server.getTickCount());
        if (denied != null) return denied;
        lease.expiresAtTick = server.getTickCount() + DEFAULT_IDLE_TIMEOUT_TICKS;
        return AuthorOperationResult.success("SESSION_SNAPSHOT", "Edit-session draft loaded", lease.draft);
    }

    /** Re-synchronizes the owner's authoritative lease after a stale client response. */
    public synchronized AuthorOperationResult<EditSessionHandle> recover(ServerPlayer player, UUID sessionId,
                                                                          ResourceLocation bookId) {
        MinecraftServer server = connectedServer(player);
        if (server == null) return notConnected();
        if (!isAdministrator(player)) {
            releasePlayer(server, player.getUUID());
            return forbidden();
        }
        pruneDisconnected(server);
        Lease lease = lease(server, sessionId);
        if (lease == null || lease.expiresAtTick <= server.getTickCount()) return expired();
        if (!lease.editorId.equals(player.getUUID())) return forbidden();
        if (!lease.bookId.equals(bookId)) {
            return AuthorOperationResult.failure(AuthorOperationResult.Status.CONFLICT,
                    "SESSION_BOOK_MISMATCH", "Edit session belongs to " + lease.bookId);
        }
        if (lease.live) {
            var active = yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager.get().active().orElse(null);
            if (active == null || !active.book().id().equals(bookId)) {
                return AuthorOperationResult.failure(AuthorOperationResult.Status.CONFLICT,
                        "LIVE_BOOK_CHANGED", "Active book changed; close this editor first");
            }
            // Recovery abandons stale live projections; it never publishes them over another revision.
            lease.draft = DraftSnapshot.from(active.book(), DraftOrigin.ACTIVE, active.revision());
            lease.savedRevision = active.revision();
        }
        lease.clearHistory();
        lease.expiresAtTick = server.getTickCount() + DEFAULT_IDLE_TIMEOUT_TICKS;
        return AuthorOperationResult.success("SESSION_RECOVERED", "Authoritative draft re-synchronized",
                lease.handle());
    }

    /** Lets the owner explicitly abandon a conflicted lease without trusting its stale client revision. */
    public synchronized AuthorOperationResult<EditSessionView> abandon(ServerPlayer player, UUID sessionId,
                                                                        ResourceLocation bookId) {
        MinecraftServer server = connectedServer(player);
        if (server == null) return notConnected();
        if (!isAdministrator(player)) {
            releasePlayer(server, player.getUUID());
            return forbidden();
        }
        ServerSessions state = servers.get(server);
        Lease lease = state == null ? null : state.byId.get(sessionId);
        if (lease == null) return expired();
        if (!lease.editorId.equals(player.getUUID())) return forbidden();
        if (!lease.bookId.equals(bookId)) {
            return AuthorOperationResult.failure(AuthorOperationResult.Status.CONFLICT,
                    "SESSION_BOOK_MISMATCH", "Edit session belongs to " + lease.bookId);
        }
        state.remove(lease);
        if (state.byId.isEmpty()) servers.remove(server);
        return AuthorOperationResult.success("SESSION_ABANDONED", "Conflicted edit session abandoned", lease.view());
    }

    synchronized AuthorOperationResult<DraftEditResult> mutate(ServerPlayer player, UUID sessionId,
                                                                ResourceLocation bookId,
                                                                String expectedDraftRevision,
                                                                Function<DraftSnapshot, AuthorOperationResult<DraftEditResult>> operation) {
        MinecraftServer server = connectedServer(player);
        if (server == null) return notConnected();
        if (!isAdministrator(player)) {
            releasePlayer(server, player.getUUID());
            return forbidden();
        }
        pruneDisconnected(server);
        return mutateAuthorized(server, player.getUUID(), sessionId, bookId, expectedDraftRevision,
                server.getTickCount(), DEFAULT_IDLE_TIMEOUT_TICKS, current -> {
                    var result = operation.apply(current);
                    Lease lease = lease(server, sessionId);
                    return lease.live && result.success() && result.value() != null
                            ? LiveEditService.commit(server, player, current, result.value()) : result;
                });
    }

    synchronized <T> AuthorOperationResult<T> read(ServerPlayer player, UUID sessionId, ResourceLocation bookId,
                                                    String expectedDraftRevision,
                                                    Function<DraftSessionState, AuthorOperationResult<T>> operation) {
        MinecraftServer server = connectedServer(player);
        if (server == null) return notConnected();
        if (!isAdministrator(player)) {
            releasePlayer(server, player.getUUID());
            return forbidden();
        }
        pruneDisconnected(server);
        Lease lease = lease(server, sessionId);
        AuthorOperationResult<T> denied = denyLease(lease, player.getUUID(), bookId,
                expectedDraftRevision, server.getTickCount());
        if (denied != null) return denied;
        lease.expiresAtTick = server.getTickCount() + DEFAULT_IDLE_TIMEOUT_TICKS;
        return operation.apply(lease.state());
    }

    synchronized AuthorOperationResult<DraftSaveResult> persist(ServerPlayer player, UUID sessionId,
                                                                 ResourceLocation bookId,
                                                                 String expectedDraftRevision,
                                                                 Function<DraftSessionState,
                                                                         AuthorOperationResult<DraftSaveResult>> operation) {
        MinecraftServer server = connectedServer(player);
        if (server == null) return notConnected();
        if (!isAdministrator(player)) {
            releasePlayer(server, player.getUUID());
            return forbidden();
        }
        pruneDisconnected(server);
        Lease lease = lease(server, sessionId);
        AuthorOperationResult<DraftSaveResult> denied = denyLease(lease, player.getUUID(), bookId,
                expectedDraftRevision, server.getTickCount());
        if (denied != null) return denied;
        if (lease.live) return AuthorOperationResult.failure(AuthorOperationResult.Status.INVALID_REQUEST,
                "LIVE_ALREADY_SAVED", "Live edits do not write to the advanced draft workspace");
        AuthorOperationResult<DraftSaveResult> result = operation.apply(lease.state());
        if (result.status() == AuthorOperationResult.Status.CONFLICT) lease.clearHistory();
        if (result.success() && result.value() != null
                && result.value().snapshot().draftRevision().equals(lease.draft.draftRevision())) {
            lease.savedRevision = lease.draft.draftRevision();
            lease.expiresAtTick = server.getTickCount() + DEFAULT_IDLE_TIMEOUT_TICKS;
        }
        return result;
    }

    synchronized AuthorOperationResult<DraftPublishResult> publish(ServerPlayer player, UUID sessionId,
                                                                    ResourceLocation bookId,
                                                                    String expectedDraftRevision,
                                                                    Function<DraftSessionState,
                                                                            AuthorOperationResult<DraftPublishResult>> operation) {
        MinecraftServer server = connectedServer(player);
        if (server == null) return notConnected();
        if (!isAdministrator(player)) {
            releasePlayer(server, player.getUUID());
            return forbidden();
        }
        pruneDisconnected(server);
        Lease lease = lease(server, sessionId);
        AuthorOperationResult<DraftPublishResult> denied = denyLease(lease, player.getUUID(), bookId,
                expectedDraftRevision, server.getTickCount());
        if (denied != null) return denied;
        if (lease.live) return AuthorOperationResult.failure(AuthorOperationResult.Status.INVALID_REQUEST,
                "LIVE_ALREADY_SAVED", "Live edits are already applied to this world");
        AuthorOperationResult<DraftPublishResult> result = operation.apply(lease.state());
        if (result.status() == AuthorOperationResult.Status.CONFLICT) lease.clearHistory();
        if (result.success() && result.value() != null
                && result.value().snapshot().draftRevision().equals(lease.draft.draftRevision())) {
            // Publishing establishes a new workspace concurrency baseline without
            // changing semantic content or the draft's saved/dirty state.
            lease.draft = result.value().snapshot();
            lease.undo.clear();
            lease.redo.clear();
            lease.expiresAtTick = server.getTickCount() + DEFAULT_IDLE_TIMEOUT_TICKS;
        }
        return result;
    }

    AuthorOperationResult<DraftEditResult> mutateAuthorized(Object serverKey, UUID editorId, UUID sessionId,
                                                             ResourceLocation bookId, String expectedDraftRevision,
                                                             long nowTick, long timeoutTicks,
                                                             Function<DraftSnapshot, AuthorOperationResult<DraftEditResult>> operation) {
        ServerSessions state = servers.get(serverKey);
        if (state != null) state.prune(nowTick);
        Lease lease = state == null ? null : state.byId.get(sessionId);
        AuthorOperationResult<DraftEditResult> authorization = authorizeMutation(
                lease, editorId, bookId, expectedDraftRevision, nowTick);
        if (!authorization.success()) return authorization;
        AuthorOperationResult<DraftEditResult> result = operation.apply(lease.draft);
        if (result.success() && result.value() != null) {
            // The candidate was fully built and validated before this single pointer swap.
            DraftSnapshot candidate = result.value().snapshot();
            if (!candidate.draftRevision().equals(lease.draft.draftRevision())) {
                // Published ID migrations affect real ledgers and cannot be undone by removing their aliases.
                if (lease.live && !candidate.book().legacyIds().equals(lease.draft.book().legacyIds())) lease.clearHistory();
                else lease.pushUndo(lease.draft);
                lease.redo.clear();
                lease.draft = candidate;
                if (lease.live) lease.savedRevision = candidate.draftRevision();
            }
            lease.expiresAtTick = nowTick + timeoutTicks;
        }
        return result;
    }

    public synchronized AuthorOperationResult<DraftEditResult> undo(ServerPlayer player, UUID sessionId,
                                                                     ResourceLocation bookId,
                                                                     String expectedDraftRevision) {
        return history(player, sessionId, bookId, expectedDraftRevision, false);
    }

    public synchronized AuthorOperationResult<DraftEditResult> redo(ServerPlayer player, UUID sessionId,
                                                                     ResourceLocation bookId,
                                                                     String expectedDraftRevision) {
        return history(player, sessionId, bookId, expectedDraftRevision, true);
    }

    private AuthorOperationResult<DraftEditResult> history(ServerPlayer player, UUID sessionId,
                                                            ResourceLocation bookId,
                                                            String expectedDraftRevision, boolean redo) {
        MinecraftServer server = connectedServer(player);
        if (server == null) return notConnected();
        if (!isAdministrator(player)) {
            releasePlayer(server, player.getUUID());
            return forbidden();
        }
        pruneDisconnected(server);
        return historyAuthorized(server, player.getUUID(), sessionId, bookId, expectedDraftRevision,
                server.getTickCount(), DEFAULT_IDLE_TIMEOUT_TICKS, redo);
    }

    AuthorOperationResult<DraftEditResult> historyAuthorized(Object serverKey, UUID editorId, UUID sessionId,
                                                               ResourceLocation bookId, String expectedDraftRevision,
                                                               long nowTick, long timeoutTicks, boolean redo) {
        ServerSessions state = servers.get(serverKey);
        if (state != null) state.prune(nowTick);
        Lease lease = state == null ? null : state.byId.get(sessionId);
        AuthorOperationResult<DraftEditResult> authorization = authorizeMutation(
                lease, editorId, bookId, expectedDraftRevision, nowTick);
        if (!authorization.success()) return authorization;
        Deque<DraftSnapshot> source = redo ? lease.redo : lease.undo;
        if (source.isEmpty()) {
            DraftEditResult unchanged = new DraftEditResult(lease.draft, List.of(), List.of());
            return AuthorOperationResult.noChange(redo ? "REDO_EMPTY" : "UNDO_EMPTY",
                    redo ? "No edit is available to redo" : "No edit is available to undo", unchanged);
        }
        DraftSnapshot previous = lease.draft;
        DraftSnapshot restored = source.peekLast();
        if (lease.live) {
            MinecraftServer server = (MinecraftServer) serverKey;
            var committed = LiveEditService.commit(server, server.getPlayerList().getPlayer(editorId), previous,
                    new DraftEditResult(restored, List.of(bookId), List.of()));
            if (!committed.success()) return committed;
            restored = committed.value().snapshot();
            lease.savedRevision = restored.draftRevision();
        }
        source.removeLast();
        if (redo) lease.pushUndo(previous);
        else lease.pushRedo(previous);
        lease.draft = restored;
        lease.expiresAtTick = nowTick + timeoutTicks;
        DraftEditResult result = new DraftEditResult(restored, List.of(bookId), List.of());
        return AuthorOperationResult.success(redo ? "DRAFT_REDONE" : "DRAFT_UNDONE",
                redo ? "Draft edit redone" : "Draft edit undone", result);
    }

    public synchronized void releasePlayer(MinecraftServer server, UUID playerId) {
        releasePlayer((Object) server, playerId);
    }

    public synchronized void clearServer(MinecraftServer server) {
        servers.remove(server);
    }

    AuthorOperationResult<EditSessionHandle> openAuthorized(Object serverKey, UUID editorId, String editorName,
                                                             DraftSnapshot draft, long nowTick, long timeoutTicks) {
        if (serverKey == null || editorId == null || draft == null || editorName == null || editorName.isBlank()
                || timeoutTicks <= 0L) {
            return AuthorOperationResult.failure(AuthorOperationResult.Status.INVALID_REQUEST, "INVALID_SESSION_REQUEST",
                    "Server, editor and draft are required");
        }
        ServerSessions state = servers.computeIfAbsent(serverKey, ignored -> new ServerSessions());
        state.prune(nowTick);
        Lease existing = state.byBook.get(draft.book().id());
        if (existing != null) {
            if (existing.editorId.equals(editorId)) {
                return AuthorOperationResult.noChange("SESSION_ALREADY_OPEN", "Edit session already open",
                        existing.handle());
            }
            return AuthorOperationResult.failure(AuthorOperationResult.Status.CONFLICT, "BOOK_ALREADY_EDITED",
                    "Book is currently edited by " + existing.editorName);
        }
        Lease lease = new Lease(UUID.randomUUID(), draft.book().id(), editorId, editorName,
                draft, nowTick + timeoutTicks);
        state.add(lease);
        return AuthorOperationResult.success("SESSION_OPENED", "Edit session opened", lease.handle());
    }

    AuthorOperationResult<EditSessionHandle> renewAuthorized(Object serverKey, UUID editorId, UUID sessionId,
                                                              String expectedDraftRevision, long nowTick,
                                                              long timeoutTicks) {
        ServerSessions state = servers.get(serverKey);
        if (state == null) return expired();
        state.prune(nowTick);
        Lease lease = state.byId.get(sessionId);
        if (lease == null) return expired();
        if (!lease.editorId.equals(editorId)) return forbidden();
        if (!lease.draft.draftRevision().equals(expectedDraftRevision)) {
            lease.clearHistory();
            return AuthorOperationResult.failure(AuthorOperationResult.Status.CONFLICT, "STALE_DRAFT_REVISION",
                    "Expected " + expectedDraftRevision + " but server has " + lease.draft.draftRevision());
        }
        lease.expiresAtTick = nowTick + timeoutTicks;
        return AuthorOperationResult.success("SESSION_RENEWED", "Edit session renewed", lease.handle());
    }

    AuthorOperationResult<EditSessionView> closeAuthorized(Object serverKey, UUID editorId, UUID sessionId,
                                                            String expectedDraftRevision, long nowTick) {
        ServerSessions state = servers.get(serverKey);
        if (state == null) return expired();
        state.prune(nowTick);
        Lease lease = state.byId.get(sessionId);
        if (lease == null) return expired();
        if (!lease.editorId.equals(editorId)) return forbidden();
        if (!lease.draft.draftRevision().equals(expectedDraftRevision)) {
            lease.clearHistory();
            return AuthorOperationResult.failure(AuthorOperationResult.Status.CONFLICT, "STALE_DRAFT_REVISION",
                    "Close rejected because the draft revision changed");
        }
        state.remove(lease);
        return AuthorOperationResult.success("SESSION_CLOSED", "Edit session closed", lease.view());
    }

    AuthorOperationResult<EditSessionView> inspectAuthorized(Object serverKey, ResourceLocation bookId, long nowTick) {
        ServerSessions state = servers.get(serverKey);
        if (state == null) {
            return AuthorOperationResult.failure(AuthorOperationResult.Status.NOT_FOUND, "NO_ACTIVE_SESSION",
                    "Book has no active edit session");
        }
        state.prune(nowTick);
        Lease lease = state.byBook.get(bookId);
        if (lease == null) {
            return AuthorOperationResult.failure(AuthorOperationResult.Status.NOT_FOUND, "NO_ACTIVE_SESSION",
                    "Book has no active edit session");
        }
        return AuthorOperationResult.success("SESSION_STATUS", "Edit session is active", lease.view());
    }

    void releasePlayer(Object serverKey, UUID playerId) {
        ServerSessions state = servers.get(serverKey);
        if (state == null) return;
        state.byId.values().stream().filter(lease -> lease.editorId.equals(playerId)).toList().forEach(state::remove);
        if (state.byId.isEmpty()) servers.remove(serverKey);
    }

    private static AuthorOperationResult<DraftEditResult> authorizeMutation(Lease lease, UUID editorId,
                                                                             ResourceLocation bookId,
                                                                             String expectedRevision, long nowTick) {
        if (lease == null || lease.expiresAtTick <= nowTick) return expired();
        if (!lease.editorId.equals(editorId)) return forbidden();
        if (!lease.bookId.equals(bookId)) {
            return AuthorOperationResult.failure(AuthorOperationResult.Status.CONFLICT, "SESSION_BOOK_MISMATCH",
                    "Edit session belongs to " + lease.bookId);
        }
        if (!lease.draft.draftRevision().equals(expectedRevision)) {
            lease.clearHistory();
            return AuthorOperationResult.failure(AuthorOperationResult.Status.CONFLICT, "STALE_DRAFT_REVISION",
                    "Expected " + expectedRevision + " but server has " + lease.draft.draftRevision());
        }
        return AuthorOperationResult.success("SESSION_AUTHORIZED", "Edit session authorized", null);
    }

    private Lease lease(Object serverKey, UUID sessionId) {
        ServerSessions state = servers.get(serverKey);
        return state == null ? null : state.byId.get(sessionId);
    }

    private static <T> AuthorOperationResult<T> denyLease(Lease lease, UUID editorId, ResourceLocation bookId,
                                                           String expectedRevision, long nowTick) {
        if (lease == null || lease.expiresAtTick <= nowTick) return expired();
        if (!lease.editorId.equals(editorId)) return forbidden();
        if (!lease.bookId.equals(bookId)) {
            return AuthorOperationResult.failure(AuthorOperationResult.Status.CONFLICT, "SESSION_BOOK_MISMATCH",
                    "Edit session belongs to " + lease.bookId);
        }
        if (!lease.draft.draftRevision().equals(expectedRevision)) {
            lease.clearHistory();
            return AuthorOperationResult.failure(AuthorOperationResult.Status.CONFLICT, "STALE_DRAFT_REVISION",
                    "Expected " + expectedRevision + " but server has " + lease.draft.draftRevision());
        }
        return null;
    }

    private void pruneDisconnected(MinecraftServer server) {
        ServerSessions state = servers.get(server);
        if (state == null) return;
        state.prune(server.getTickCount());
        state.byId.values().stream().filter(lease -> {
            ServerPlayer owner = server.getPlayerList().getPlayer(lease.editorId);
            return owner == null || !isAdministrator(owner);
        }).toList().forEach(state::remove);
    }

    private static MinecraftServer connectedServer(ServerPlayer player) {
        if (player == null || player.getServer() == null) return null;
        MinecraftServer server = player.getServer();
        return server.getPlayerList().getPlayer(player.getUUID()) == player ? server : null;
    }

    private static boolean isAdministrator(ServerPlayer player) {
        return player.createCommandSourceStack().hasPermission(2);
    }

    private static <T> AuthorOperationResult<T> forbidden() {
        return AuthorOperationResult.failure(AuthorOperationResult.Status.FORBIDDEN, "EDITOR_PERMISSION_REQUIRED",
                "Permission level 2 is required for task-book editing");
    }

    private static <T> AuthorOperationResult<T> notConnected() {
        return AuthorOperationResult.failure(AuthorOperationResult.Status.INVALID_REQUEST, "PLAYER_NOT_CONNECTED",
                "Editor must be connected to the target server");
    }

    private static <T> AuthorOperationResult<T> expired() {
        return AuthorOperationResult.failure(AuthorOperationResult.Status.EXPIRED, "SESSION_EXPIRED",
                "Edit session is missing or expired");
    }

    private static final class ServerSessions {
        private final Map<UUID, Lease> byId = new HashMap<>();
        private final Map<ResourceLocation, Lease> byBook = new HashMap<>();

        private void add(Lease lease) {
            byId.put(lease.sessionId, lease);
            byBook.put(lease.bookId, lease);
        }

        private void remove(Lease lease) {
            byId.remove(lease.sessionId);
            byBook.remove(lease.bookId, lease);
        }

        private void prune(long nowTick) {
            byId.values().stream().filter(lease -> lease.expiresAtTick <= nowTick).toList().forEach(this::remove);
        }
    }

    private static final class Lease {
        private final UUID sessionId;
        private final ResourceLocation bookId;
        private final UUID editorId;
        private final String editorName;
        private DraftSnapshot draft;
        private String savedRevision;
        private boolean live;
        private long expiresAtTick;
        private final Deque<DraftSnapshot> undo = new ArrayDeque<>();
        private final Deque<DraftSnapshot> redo = new ArrayDeque<>();

        private Lease(UUID sessionId, ResourceLocation bookId, UUID editorId, String editorName,
                      DraftSnapshot draft, long expiresAtTick) {
            this.sessionId = sessionId;
            this.bookId = bookId;
            this.editorId = editorId;
            this.editorName = editorName;
            this.draft = draft;
            this.savedRevision = draft.draftRevision();
            this.expiresAtTick = expiresAtTick;
        }

        private EditSessionView view() {
            return new EditSessionView(bookId, editorId, editorName, draft.baseRevision(),
                    draft.draftRevision(), savedRevision, expiresAtTick, undo.size(), redo.size());
        }

        private DraftSessionState state() { return new DraftSessionState(draft, savedRevision); }

        private EditSessionHandle handle() {
            return new EditSessionHandle(sessionId, view());
        }

        private void pushUndo(DraftSnapshot snapshot) {
            pushBounded(undo, snapshot);
        }

        private void pushRedo(DraftSnapshot snapshot) {
            pushBounded(redo, snapshot);
        }

        private static void pushBounded(Deque<DraftSnapshot> history, DraftSnapshot snapshot) {
            if (history.size() >= MAX_HISTORY_STEPS) history.removeFirst();
            history.addLast(snapshot);
        }

        private void clearHistory() {
            undo.clear();
            redo.clear();
        }
    }
}
