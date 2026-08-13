package yourscraft.jasdewstarfield.brnquest.author;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Publishes a clean server draft into workspace without deploying or reloading it. */
public final class DraftPublishService {
    private final EditSessionService sessions;
    private final DraftRevisionGuard revisions;
    private final WorkspacePublishRepository workspace;

    public DraftPublishService() {
        this(EditSessionService.get(), new DraftRepository(), new WorkspacePublishRepository());
    }

    DraftPublishService(EditSessionService sessions, DraftRepository drafts, WorkspacePublishRepository workspace) {
        this.sessions = sessions;
        this.revisions = new DraftRevisionGuard(drafts);
        this.workspace = workspace;
    }

    public AuthorOperationResult<DraftPublishResult> publish(ServerPlayer player, UUID sessionId,
                                                              ResourceLocation bookId, String expectedDraftRevision) {
        AuthorOperationResult<DraftPublishResult> result = sessions.publish(player, sessionId, bookId,
                expectedDraftRevision, state -> {
            if (state.dirty()) {
                return AuthorOperationResult.failure(AuthorOperationResult.Status.CONFLICT, "UNSAVED_DRAFT",
                        "Save the current draft before publishing it");
            }
            List<Diagnostic> diagnostics = AuthorValidationService.full(state.snapshot().book());
            if (AuthorValidationService.blocksCommit(diagnostics)) {
                return AuthorOperationResult.failure(AuthorOperationResult.Status.INVALID_REQUEST,
                        "DRAFT_VALIDATION_FAILED", "Draft must pass full validation before publish",
                        new DraftPublishResult(state.snapshot(), "", null, null, diagnostics));
            }
            AuthorOperationResult<RevisionCheck> inspected = revisions.inspect(player.getServer(), state);
            if (!inspected.success()) return failureLike(inspected);
            RevisionCheck checked = addWorkspaceCreationConflict(state.snapshot(), inspected.value());
            if (checked.hasConflicts()) {
                return AuthorOperationResult.failure(AuthorOperationResult.Status.CONFLICT, "REVISION_CONFLICT",
                        "Draft publish rejected by revision guard",
                        new DraftPublishResult(state.snapshot(), checked.revisions().workspaceRevision(), null,
                                checked, diagnostics));
            }
            String expectedWorkspace = checked.revisions().workspaceRevision();
            AuthorOperationResult<DraftPublishResult> published = workspace.publish(player.getServer(),
                    state.snapshot(), expectedWorkspace);
            if (!published.success()) return published;
            DraftPublishResult value = published.value();
            DraftPublishResult enriched = new DraftPublishResult(value.snapshot(), value.previousWorkspaceRevision(),
                    value.backup(), checked, diagnostics);
            return published.status() == AuthorOperationResult.Status.NO_CHANGE
                    ? AuthorOperationResult.noChange(published.code(), published.message(), enriched)
                    : AuthorOperationResult.success(published.code(), published.message(), enriched);
        });
        String before = result.value() == null ? "" : result.value().previousWorkspaceRevision();
        String after = result.success() && result.value() != null
                ? result.value().snapshot().draftRevision() : before;
        AuthorAuditLog.record(player, "draft_publish", bookId.toString(), before, after, result);
        return result;
    }

    private static RevisionCheck addWorkspaceCreationConflict(DraftSnapshot draft, RevisionCheck original) {
        String workspaceRevision = original.revisions().workspaceRevision();
        if (workspaceRevision.isBlank() || workspaceRevision.equals(draft.draftRevision())
                || draft.origin() == DraftOrigin.WORKSPACE) return original;
        List<RevisionConflict> conflicts = new ArrayList<>(original.conflicts());
        conflicts.add(new RevisionConflict("WORKSPACE_ALREADY_EXISTS", "", workspaceRevision,
                "Create a draft from workspace before replacing an existing workspace book"));
        return new RevisionCheck(original.revisions(), conflicts);
    }

    private static <T> AuthorOperationResult<T> failureLike(AuthorOperationResult<?> source) {
        return AuthorOperationResult.failure(source.status(), source.code(), source.message());
    }
}
