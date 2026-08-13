package yourscraft.jasdewstarfield.brnquest.author;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic;

import java.util.List;
import java.util.UUID;

/** Validates and atomically saves the current target-server session draft. */
public final class DraftPersistenceService {
    private final EditSessionService sessions;
    private final DraftRepository repository;
    private final DraftRevisionGuard revisions;

    public DraftPersistenceService() {
        this(EditSessionService.get(), new DraftRepository());
    }

    DraftPersistenceService(EditSessionService sessions, DraftRepository repository) {
        this.sessions = sessions;
        this.repository = repository;
        this.revisions = new DraftRevisionGuard(repository);
    }

    public AuthorOperationResult<DraftSaveResult> save(ServerPlayer player, UUID sessionId,
                                                        ResourceLocation bookId, String expectedDraftRevision) {
        return sessions.persist(player, sessionId, bookId, expectedDraftRevision, state -> {
            List<Diagnostic> diagnostics = AuthorValidationService.full(state.snapshot().book());
            if (AuthorValidationService.blocksCommit(diagnostics)) {
                return AuthorOperationResult.failure(AuthorOperationResult.Status.INVALID_REQUEST,
                        "DRAFT_VALIDATION_FAILED", "Draft must pass full validation before save",
                        new DraftSaveResult(state.snapshot(), state.savedRevision(), null, null, diagnostics));
            }
            AuthorOperationResult<RevisionCheck> inspected = revisions.inspect(player.getServer(), state);
            if (!inspected.success()) {
                return AuthorOperationResult.failure(inspected.status(), inspected.code(), inspected.message());
            }
            RevisionCheck check = inspected.value();
            if (check.hasConflicts()) {
                return AuthorOperationResult.failure(AuthorOperationResult.Status.CONFLICT, "REVISION_CONFLICT",
                        "Draft save rejected by revision guard",
                        new DraftSaveResult(state.snapshot(), state.savedRevision(), null, check, diagnostics));
            }
            AuthorOperationResult<DraftSaveResult> saved = repository.save(player.getServer(), state.snapshot(),
                    state.savedRevision());
            if (!saved.success()) return saved;
            DraftSaveResult value = saved.value();
            DraftSaveResult result = new DraftSaveResult(value.snapshot(), value.previousRevision(), value.backup(),
                    check, diagnostics);
            return saved.status() == AuthorOperationResult.Status.NO_CHANGE
                    ? AuthorOperationResult.noChange(saved.code(), saved.message(), result)
                    : AuthorOperationResult.success(saved.code(), saved.message(), result);
        });
    }
}
