package yourscraft.jasdewstarfield.brnquest.author;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Function;

/**
 * Server-scoped single-writer leases for local or remote administrators.
 * Draft content never lives on the connecting client's filesystem.
 */
public final class EditSessionService {
    public static final long DEFAULT_IDLE_TIMEOUT_TICKS = 20L * 60L * 5L;
    private static final EditSessionService INSTANCE = new EditSessionService();

    // Weak server keys keep integrated-server restarts and GameTest servers isolated
    // without retaining a stopped MinecraftServer for the rest of the client process.
    private final Map<Object, ServerSessions> servers = new WeakHashMap<>();

    // Package visibility lets deterministic unit tests use an isolated lease table.
    EditSessionService() {}

    public static EditSessionService get() {
        return INSTANCE;
    }

    public synchronized AuthorOperationResult<EditSessionHandle> open(ServerPlayer player, DraftSnapshot draft) {
        MinecraftServer server = connectedServer(player);
        if (server == null) return notConnected();
        if (!isAdministrator(player)) return forbidden();
        pruneDisconnected(server);
        return openAuthorized(server, player.getUUID(), player.getScoreboardName(), draft,
                server.getTickCount(), DEFAULT_IDLE_TIMEOUT_TICKS);
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
                server.getTickCount(), DEFAULT_IDLE_TIMEOUT_TICKS, operation);
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
            lease.draft = result.value().snapshot();
            lease.expiresAtTick = nowTick + timeoutTicks;
        }
        return result;
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
            return AuthorOperationResult.failure(AuthorOperationResult.Status.CONFLICT, "STALE_DRAFT_REVISION",
                    "Expected " + expectedRevision + " but server has " + lease.draft.draftRevision());
        }
        return AuthorOperationResult.success("SESSION_AUTHORIZED", "Edit session authorized", null);
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
        private long expiresAtTick;

        private Lease(UUID sessionId, ResourceLocation bookId, UUID editorId, String editorName,
                      DraftSnapshot draft, long expiresAtTick) {
            this.sessionId = sessionId;
            this.bookId = bookId;
            this.editorId = editorId;
            this.editorName = editorName;
            this.draft = draft;
            this.expiresAtTick = expiresAtTick;
        }

        private EditSessionView view() {
            return new EditSessionView(bookId, editorId, editorName, draft.baseRevision(),
                    draft.draftRevision(), expiresAtTick);
        }

        private EditSessionHandle handle() {
            return new EditSessionHandle(sessionId, view());
        }
    }
}
