package yourscraft.jasdewstarfield.brnquest.author;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.BrnQuestConstants;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.data.ChapterDefinition;
import yourscraft.jasdewstarfield.brnquest.data.ChapterGroupDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.data.TaskDefinition;
import yourscraft.jasdewstarfield.brnquest.data.RewardDefinition;
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
        AuthorOperationResult<DraftSnapshot> prepared = prepareFromActive(server, active.book(), active.revision());
        return prepared.success() ? repository.create(server, prepared.value()) : prepared;
    }

    /** Creates a new current draft version after verifying the exact version selected by the client. */
    public AuthorOperationResult<DraftSnapshot> replaceFromActive(ServerPlayer player, String expectedDraftRevision) {
        MinecraftServer server = authorizedServer(player);
        if (server == null) return authorizationFailure(player);
        var active = QuestBookManager.get().active().orElse(null);
        if (active == null) {
            return AuthorOperationResult.failure(AuthorOperationResult.Status.NOT_FOUND, "NO_ACTIVE_BOOK",
                    "No active task book is available");
        }
        AuthorOperationResult<EditSessionView> occupied = EditSessionService.get().inspect(player, active.book().id());
        if (occupied.success()) {
            return AuthorOperationResult.failure(AuthorOperationResult.Status.CONFLICT, "BOOK_ALREADY_EDITED",
                    "Close the active edit session before creating another draft version");
        }
        if (occupied.status() != AuthorOperationResult.Status.NOT_FOUND) {
            return AuthorOperationResult.failure(occupied.status(), occupied.code(), occupied.message());
        }
        AuthorOperationResult<DraftSnapshot> prepared = prepareFromActive(server, active.book(), active.revision());
        return prepared.success() ? repository.replace(server, prepared.value(), expectedDraftRevision) : prepared;
    }

    /** Captures the existing workspace as the publish target baseline while copying current active content. */
    private AuthorOperationResult<DraftSnapshot> prepareFromActive(MinecraftServer server,
                                                                    QuestBookDefinition activeBook,
                                                                    String activeRevision) {
        AuthorOperationResult<DraftSnapshot> workspace = repository.readWorkspace(server, activeBook.id());
        if (workspace.success()) {
            return AuthorOperationResult.success("ACTIVE_DRAFT_PREPARED", "Current book copied over workspace baseline",
                    activeDraft(activeBook, activeRevision, workspace.value()));
        }
        if (workspace.status() != AuthorOperationResult.Status.NOT_FOUND) return failureLike(workspace);
        return AuthorOperationResult.success("ACTIVE_DRAFT_PREPARED", "Current book copied from active baseline",
                activeDraft(activeBook, activeRevision, null));
    }

    static DraftSnapshot activeDraft(QuestBookDefinition activeBook, String activeRevision,
                                     DraftSnapshot workspace) {
        // Origin identifies the revision that must remain unchanged before publication. When a
        // workspace already exists, it is the replacement target even though content comes from active.
        return workspace == null
                ? DraftSnapshot.from(activeBook, DraftOrigin.ACTIVE, activeRevision)
                : DraftSnapshot.from(activeBook, DraftOrigin.WORKSPACE, workspace.draftRevision());
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
        return repository.create(server, DraftSnapshot.from(book, DraftOrigin.EMPTY, ""));
    }

    /** Stores a recovery copy under a new book ID without changing stable content IDs. */
    public AuthorOperationResult<DraftSnapshot> createRecoveryCopy(ServerPlayer player, DraftSnapshot source,
                                                                    ResourceLocation targetBookId) {
        MinecraftServer server = authorizedServer(player);
        if (server == null) return authorizationFailure(player);
        if (source == null || targetBookId == null || targetBookId.equals(source.book().id())) {
            return AuthorOperationResult.failure(AuthorOperationResult.Status.INVALID_REQUEST,
                    "INVALID_RECOVERY_BOOK", "Recovery copy requires a different valid book ID");
        }
        QuestBookDefinition copy = recoveryCopy(source.book(), targetBookId);
        return repository.create(server, DraftSnapshot.from(copy, DraftOrigin.EMPTY, ""));
    }

    static QuestBookDefinition recoveryCopy(QuestBookDefinition original, ResourceLocation targetBookId) {
        List<ChapterGroupDefinition> groups = original.chapterGroups().stream()
                .map(group -> new ChapterGroupDefinition(targetBookId, group.id(), group.title(), group.order(),
                        group.icon(), group.description(), group.extensions()))
                .toList();
        List<ChapterDefinition> chapters = original.chapters().stream().map(chapter ->
                new ChapterDefinition(targetBookId, chapter.id(), chapter.groupId(), chapter.title(), chapter.icon(),
                        chapter.order(), chapter.quests().stream().map(quest -> copyQuest(targetBookId, quest)).toList(),
                        chapter.extensions()))
                .toList();
        QuestBookDefinition copy = new QuestBookDefinition(targetBookId, original.schemaVersion(),
                original.title() + " (recovered)", groups, chapters, original.legacyIds(),
                original.localization(), original.extensions());
        return copy;
    }

    private static QuestDefinition copyQuest(ResourceLocation bookId, QuestDefinition quest) {
        List<TaskDefinition> tasks = quest.tasks().stream().map(task -> new TaskDefinition(bookId, task.id(),
                task.typeId(), task.config(), task.optional())).toList();
        List<RewardDefinition> rewards = quest.rewards().stream().map(reward -> new RewardDefinition(bookId,
                reward.id(), reward.typeId(), reward.config(), reward.claimPolicy(), reward.teamReward())).toList();
        return new QuestDefinition(bookId, quest.id(), quest.chapterId(), quest.title(), quest.subtitle(),
                quest.description(), quest.icon(), quest.x(), quest.y(), quest.dependencies(), tasks, rewards,
                quest.legacyId(), quest.appearance(), quest.behavior(), quest.extensions());
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

    private static <T> AuthorOperationResult<T> failureLike(AuthorOperationResult<?> source) {
        return AuthorOperationResult.failure(source.status(), source.code(), source.message());
    }
}
