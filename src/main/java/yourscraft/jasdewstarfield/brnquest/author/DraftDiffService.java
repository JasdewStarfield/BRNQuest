package yourscraft.jasdewstarfield.brnquest.author;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;

import java.util.UUID;

/** Authorized semantic diff previews against saved draft, workspace, or active state. */
public final class DraftDiffService {
    public enum Baseline { SAVED_DRAFT, WORKSPACE, ACTIVE }

    private final EditSessionService sessions;
    private final DraftRepository repository;

    public DraftDiffService() {
        this(EditSessionService.get(), new DraftRepository());
    }

    DraftDiffService(EditSessionService sessions, DraftRepository repository) {
        this.sessions = sessions;
        this.repository = repository;
    }

    public AuthorOperationResult<QuestBookDiff> preview(ServerPlayer player, UUID sessionId,
                                                         ResourceLocation bookId, String expectedDraftRevision,
                                                         Baseline baseline) {
        return sessions.read(player, sessionId, bookId, expectedDraftRevision, state -> {
            QuestBookDefinition before;
            switch (baseline) {
                case SAVED_DRAFT -> {
                    AuthorOperationResult<DraftSnapshot> loaded = repository.load(player.getServer(), bookId);
                    if (!loaded.success()) return failureLike(loaded);
                    before = loaded.value().book();
                }
                case WORKSPACE -> {
                    AuthorOperationResult<DraftSnapshot> loaded = repository.readWorkspace(player.getServer(), bookId);
                    if (!loaded.success() && loaded.status() != AuthorOperationResult.Status.NOT_FOUND) return failureLike(loaded);
                    before = loaded.success() ? loaded.value().book() : null;
                }
                case ACTIVE -> before = QuestBookManager.get().active()
                        .filter(active -> active.book().id().equals(bookId)).map(active -> active.book()).orElse(null);
                default -> throw new IllegalStateException("Unhandled diff baseline " + baseline);
            }
            QuestBookDiff diff = QuestBookDiffer.diff(before, state.snapshot().book());
            return diff.empty() ? AuthorOperationResult.noChange("NO_SEMANTIC_DIFF", "No semantic differences", diff)
                    : AuthorOperationResult.success("SEMANTIC_DIFF", "Semantic differences generated", diff);
        });
    }

    private static <T> AuthorOperationResult<T> failureLike(AuthorOperationResult<?> source) {
        return AuthorOperationResult.failure(source.status(), source.code(), source.message());
    }
}
