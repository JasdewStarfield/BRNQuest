package yourscraft.jasdewstarfield.brnquest.author;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Publishes a clean server draft into workspace without deploying or reloading it. */
public final class DraftPublishService {
    private final EditSessionService sessions;
    private final DraftRepository drafts;
    private final DraftRevisionGuard revisions;
    private final WorkspacePublishRepository workspace;

    public DraftPublishService() {
        this(EditSessionService.get(), new DraftRepository(), new WorkspacePublishRepository());
    }

    DraftPublishService(EditSessionService sessions, DraftRepository drafts, WorkspacePublishRepository workspace) {
        this.sessions = sessions;
        this.drafts = drafts;
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
            List<Diagnostic> baselineDiagnostics = sourceBaselineDiagnostics(player, state.snapshot());
            if (AuthorValidationService.blocksCommit(diagnostics, baselineDiagnostics)) {
                return AuthorOperationResult.failure(AuthorOperationResult.Status.INVALID_REQUEST,
                        "DRAFT_VALIDATION_FAILED", "Draft introduced a validation error relative to its source",
                        new DraftPublishResult(state.snapshot(), "", null, null, diagnostics));
            }
            AuthorOperationResult<RevisionCheck> inspected = revisions.inspect(player.getServer(), state);
            if (!inspected.success()) return failureLike(inspected);
            RevisionCheck checked = addWorkspaceCreationConflict(state.snapshot(), inspected.value());
            if (checked.hasConflicts()) {
                RevisionConflict first = checked.conflicts().getFirst();
                return AuthorOperationResult.failure(AuthorOperationResult.Status.CONFLICT, "REVISION_CONFLICT",
                        "Draft publish rejected by revision guard: " + first.code() + " - " + first.message(),
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

    /**
     * Publishing may preserve diagnostics already present in the revision from which
     * an ACTIVE or WORKSPACE draft was created, but it may not introduce new ones.
     * IMPORT/EMPTY/UNKNOWN drafts have no trusted task-book baseline and stay strict.
     */
    private List<Diagnostic> sourceBaselineDiagnostics(ServerPlayer player, DraftSnapshot draft) {
        DraftSnapshot baseline = switch (draft.origin()) {
            case ACTIVE -> QuestBookManager.get().active()
                    .filter(active -> active.book().id().equals(draft.book().id()))
                    .filter(active -> active.revision().equals(draft.baseRevision()))
                    .map(active -> DraftSnapshot.from(active.book(), DraftOrigin.ACTIVE, active.revision()))
                    .orElse(null);
            case WORKSPACE -> {
                AuthorOperationResult<DraftSnapshot> workspace = drafts.readWorkspace(player.getServer(),
                        draft.book().id());
                yield workspace.success() && workspace.value().draftRevision().equals(draft.baseRevision())
                        ? workspace.value() : null;
            }
            case EMPTY, IMPORT, UNKNOWN -> null;
        };
        return baseline == null ? List.of() : AuthorValidationService.full(baseline.book());
    }

    static RevisionCheck addWorkspaceCreationConflict(DraftSnapshot draft, RevisionCheck original) {
        String workspaceRevision = original.revisions().workspaceRevision();
        // An ACTIVE draft may replace a workspace that is still byte-for-byte the
        // same source revision. A different workspace revision remains protected.
        if (workspaceRevision.isBlank() || workspaceRevision.equals(draft.draftRevision())
                || workspaceRevision.equals(draft.baseRevision())
                || draft.origin() == DraftOrigin.WORKSPACE) return original;
        List<RevisionConflict> conflicts = new ArrayList<>(original.conflicts());
        conflicts.add(new RevisionConflict("WORKSPACE_ALREADY_EXISTS", draft.baseRevision(), workspaceRevision,
                "Workspace differs from the source revision; open its draft before replacing it"));
        return new RevisionCheck(original.revisions(), conflicts);
    }

    private static <T> AuthorOperationResult<T> failureLike(AuthorOperationResult<?> source) {
        return AuthorOperationResult.failure(source.status(), source.code(), source.message());
    }
}
