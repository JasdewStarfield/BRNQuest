package yourscraft.jasdewstarfield.brnquest.author;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.BrnQuestConstants;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;

import java.util.List;
import java.util.Map;

/** Server-side entry point for creating isolated author drafts from supported sources. */
public final class DraftService {
    private final DraftRepository repository;

    public DraftService() {
        this(new DraftRepository());
    }

    DraftService(DraftRepository repository) {
        this.repository = repository;
    }

    public AuthorOperationResult<DraftSnapshot> createFromActive(ServerPlayer player) {
        MinecraftServer server = authorizedServer(player);
        if (server == null) return authorizationFailure(player);
        var active = QuestBookManager.get().active().orElse(null);
        if (active == null) {
            return AuthorOperationResult.failure(AuthorOperationResult.Status.NOT_FOUND, "NO_ACTIVE_BOOK",
                    "No active task book is available");
        }
        return repository.create(server, DraftSnapshot.of(active.book(), active.revision()));
    }

    public AuthorOperationResult<DraftSnapshot> createFromWorkspace(ServerPlayer player, ResourceLocation bookId) {
        MinecraftServer server = authorizedServer(player);
        if (server == null) return authorizationFailure(player);
        var source = repository.readWorkspace(server, bookId);
        if (!source.success()) return source;
        return repository.create(server, source.optionalValue().orElseThrow());
    }

    public AuthorOperationResult<DraftSnapshot> createEmpty(ServerPlayer player, ResourceLocation bookId, String title) {
        MinecraftServer server = authorizedServer(player);
        if (server == null) return authorizationFailure(player);
        if (bookId == null || title == null || title.isBlank()) {
            return AuthorOperationResult.failure(AuthorOperationResult.Status.INVALID_REQUEST, "INVALID_EMPTY_BOOK",
                    "Book ID and title are required");
        }
        var book = new QuestBookDefinition(bookId, BrnQuestConstants.DATA_SCHEMA, title,
                List.of(), List.of(), Map.of());
        return repository.create(server, DraftSnapshot.of(book, ""));
    }

    private static MinecraftServer authorizedServer(ServerPlayer player) {
        if (player == null || player.getServer() == null) return null;
        MinecraftServer server = player.getServer();
        // Resolve identity and permission on the target server for every request;
        // a remote client's local operator state is never authoritative.
        if (server.getPlayerList().getPlayer(player.getUUID()) != player
                || !player.createCommandSourceStack().hasPermission(2)) return null;
        return server;
    }

    private static <T> AuthorOperationResult<T> authorizationFailure(ServerPlayer player) {
        if (player == null || player.getServer() == null
                || player.getServer().getPlayerList().getPlayer(player.getUUID()) != player) {
            return AuthorOperationResult.failure(AuthorOperationResult.Status.INVALID_REQUEST, "PLAYER_NOT_CONNECTED",
                    "Editor must be connected to the target server");
        }
        return AuthorOperationResult.failure(AuthorOperationResult.Status.FORBIDDEN, "EDITOR_PERMISSION_REQUIRED",
                "Permission level 2 is required for draft creation");
    }
}
