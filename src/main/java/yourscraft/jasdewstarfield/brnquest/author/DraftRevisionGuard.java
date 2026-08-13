package yourscraft.jasdewstarfield.brnquest.author;

import net.minecraft.server.MinecraftServer;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;

import java.util.ArrayList;

/** Resolves active/base/draft/disk/workspace revisions before persistence. */
public final class DraftRevisionGuard {
    private final DraftRepository repository;

    public DraftRevisionGuard(DraftRepository repository) {
        this.repository = repository;
    }

    public AuthorOperationResult<RevisionCheck> inspect(MinecraftServer server, DraftSessionState state) {
        DraftSnapshot draft = state.snapshot();
        AuthorOperationResult<DraftSnapshot> disk = repository.load(server, draft.book().id());
        if (!disk.success()) return failureLike(disk);
        AuthorOperationResult<DraftSnapshot> workspace = repository.readWorkspace(server, draft.book().id());
        String workspaceRevision = workspace.success() ? workspace.value().draftRevision() : "";
        if (!workspace.success() && workspace.status() != AuthorOperationResult.Status.NOT_FOUND) return failureLike(workspace);
        String activeRevision = QuestBookManager.get().active()
                .filter(active -> active.book().id().equals(draft.book().id()))
                .map(active -> active.revision()).orElse("");
        return AuthorOperationResult.success("REVISIONS_INSPECTED", "Author revisions inspected",
                evaluate(draft, state.savedRevision(), disk.value().draftRevision(), activeRevision, workspaceRevision));
    }

    static RevisionCheck evaluate(DraftSnapshot draft, String savedRevision, String diskRevision,
                                  String activeRevision, String workspaceRevision) {
        RevisionVector vector = new RevisionVector(activeRevision, draft.baseRevision(), draft.draftRevision(),
                diskRevision, workspaceRevision);
        var conflicts = new ArrayList<RevisionConflict>();
        if (!diskRevision.equals(savedRevision)) conflicts.add(new RevisionConflict("DISK_DRAFT_CHANGED",
                savedRevision, diskRevision, "Saved draft changed outside this edit session"));
        switch (draft.origin()) {
            case ACTIVE -> mismatch(conflicts, "ACTIVE_BASE_CHANGED", draft.baseRevision(), activeRevision,
                    "Active task book changed since this draft was created");
            case WORKSPACE -> mismatch(conflicts, "WORKSPACE_BASE_CHANGED", draft.baseRevision(), workspaceRevision,
                    "Workspace task book changed since this draft was created");
            case EMPTY -> {
                if (!workspaceRevision.isBlank()) conflicts.add(new RevisionConflict("WORKSPACE_CREATED",
                        "", workspaceRevision, "Workspace book was created after the empty draft"));
            }
            case IMPORT, UNKNOWN -> { /* Their source revision is not an active/workspace concurrency token. */ }
        }
        return new RevisionCheck(vector, conflicts);
    }

    private static void mismatch(ArrayList<RevisionConflict> conflicts, String code, String expected,
                                 String actual, String message) {
        if (!expected.equals(actual)) conflicts.add(new RevisionConflict(code, expected, actual, message));
    }

    private static <T> AuthorOperationResult<T> failureLike(AuthorOperationResult<?> source) {
        return AuthorOperationResult.failure(source.status(), source.code(), source.message());
    }
}
