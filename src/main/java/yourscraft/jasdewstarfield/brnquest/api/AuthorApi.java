package yourscraft.jasdewstarfield.brnquest.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.author.*;
import yourscraft.jasdewstarfield.brnquest.compat.ftb.FtbImportService;
import yourscraft.jasdewstarfield.brnquest.workspace.WorkspaceDeploymentService;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Public facade for the server-authoritative author workflow.
 * Callers submit stable IDs and revisions; no method accepts a client-owned whole-book replacement.
 */
@ApiStatus(ApiStability.EXPERIMENTAL)
public final class AuthorApi {
    private AuthorApi() {}

    public static AuthorOperationResult<DraftSnapshot> createEmpty(ServerPlayer actor, ResourceLocation bookId,
                                                                    String title) {
        return new DraftService().createEmpty(actor, bookId, title);
    }

    /** Stable entry point for ID-based chapter, quest, task, reward, and dependency mutations. */
    public static DraftEditService editor() {
        return new DraftEditService();
    }

    public static AuthorOperationResult<DraftSnapshot> createFromActive(ServerPlayer actor) {
        return new DraftService().createFromActive(actor);
    }

    public static AuthorOperationResult<DraftSnapshot> createFromWorkspace(ServerPlayer actor,
                                                                            ResourceLocation bookId) {
        return new DraftService().createFromWorkspace(actor, bookId);
    }

    public static AuthorOperationResult<EditSessionHandle> open(ServerPlayer actor, ResourceLocation bookId) {
        if (actor == null || actor.getServer() == null) {
            return AuthorOperationResult.failure(AuthorOperationResult.Status.INVALID_REQUEST,
                    "PLAYER_NOT_CONNECTED", "Editor must be connected to the target server");
        }
        AuthorOperationResult<DraftSnapshot> loaded = new DraftRepository().load(actor.getServer(), bookId);
        if (!loaded.success()) return failureLike(loaded);
        return EditSessionService.get().open(actor, loaded.value());
    }

    public static AuthorOperationResult<EditSessionHandle> renew(ServerPlayer actor, UUID sessionId,
                                                                  String expectedDraftRevision) {
        return EditSessionService.get().renew(actor, sessionId, expectedDraftRevision);
    }

    public static AuthorOperationResult<EditSessionView> close(ServerPlayer actor, UUID sessionId,
                                                                String expectedDraftRevision) {
        return EditSessionService.get().close(actor, sessionId, expectedDraftRevision);
    }

    public static AuthorOperationResult<DraftEditResult> validate(ServerPlayer actor, UUID sessionId,
                                                                   ResourceLocation bookId,
                                                                   String expectedDraftRevision) {
        return new DraftEditService().validate(actor, sessionId, bookId, expectedDraftRevision);
    }

    public static AuthorOperationResult<QuestBookDiff> diff(ServerPlayer actor, UUID sessionId,
                                                             ResourceLocation bookId, String expectedDraftRevision,
                                                             DraftDiffService.Baseline baseline) {
        return new DraftDiffService().preview(actor, sessionId, bookId, expectedDraftRevision, baseline);
    }

    public static AuthorOperationResult<DraftSaveResult> save(ServerPlayer actor, UUID sessionId,
                                                               ResourceLocation bookId,
                                                               String expectedDraftRevision) {
        return new DraftPersistenceService().save(actor, sessionId, bookId, expectedDraftRevision);
    }

    public static AuthorOperationResult<DraftPublishResult> publish(ServerPlayer actor, UUID sessionId,
                                                                     ResourceLocation bookId,
                                                                     String expectedDraftRevision) {
        return new DraftPublishService().publish(actor, sessionId, bookId, expectedDraftRevision);
    }

    public static AuthorOperationResult<List<BackupDescriptor>> backups(ServerPlayer actor, BackupKind kind) {
        return new AuthorBackupService().list(actor, kind);
    }

    public static AuthorOperationResult<BackupRestorePreview> previewRestore(ServerPlayer actor, BackupKind kind,
                                                                              String backupId) {
        return new AuthorBackupService().preview(actor, kind, backupId);
    }

    public static AuthorOperationResult<BackupRestoreResult> restore(ServerPlayer actor, BackupKind kind,
                                                                      String backupId,
                                                                      String expectedCurrentRevision) {
        return new AuthorBackupService().restore(actor, kind, backupId, expectedCurrentRevision);
    }

    public static AuthorOperationResult<WorkspaceDeploymentService.DeploymentResult> deploy(ServerPlayer actor,
                                                                                             boolean replace) {
        AuthorOperationResult<WorkspaceDeploymentService.DeploymentResult> denied = requireAdministrator(actor);
        if (denied != null) return denied;
        try {
            var deployed = new WorkspaceDeploymentService().deploy(actor.getServer(), replace);
            if (deployed.status() == WorkspaceDeploymentService.Status.ALREADY_DEPLOYED) {
                return AuthorOperationResult.failure(AuthorOperationResult.Status.CONFLICT, "WORLD_PACK_EXISTS",
                        "Use explicit replacement to overwrite the deployed world pack");
            }
            return AuthorOperationResult.success("WORKSPACE_DEPLOYED", "Workspace deployed without reload", deployed);
        } catch (IOException exception) {
            return AuthorOperationResult.failure(AuthorOperationResult.Status.IO_FAILURE, "WORKSPACE_DEPLOY_FAILED",
                    exception.getMessage());
        }
    }

    public static CompletableFuture<AuthorOperationResult<Void>> reload(ServerPlayer actor) {
        AuthorOperationResult<Void> denied = requireAdministrator(actor);
        if (denied != null) return CompletableFuture.completedFuture(denied);
        return new WorkspaceDeploymentService().reloadIncludingWorkspace(actor.getServer())
                .handle((ignored, error) -> {
                    if (error != null) return AuthorOperationResult.failure(AuthorOperationResult.Status.IO_FAILURE,
                            "RELOAD_FAILED", error.getMessage());
                    if (yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager.get().lastReport().hasFatal()) {
                        return AuthorOperationResult.failure(AuthorOperationResult.Status.INVALID_REQUEST,
                                "RELOAD_VALIDATION_FAILED", "Previous active snapshot was retained");
                    }
                    return AuthorOperationResult.success("RELOAD_COMPLETE", "Active snapshot reloaded", null);
                });
    }

    public static AuthorOperationResult<FtbImportService.ImportExecution> importFtbDraft(ServerPlayer actor,
                                                                                          String source,
                                                                                          String namespace,
                                                                                          String bookId,
                                                                                          boolean dryRun) {
        AuthorOperationResult<FtbImportService.ImportExecution> denied = requireAdministrator(actor);
        if (denied != null) return denied;
        try {
            var execution = new FtbImportService().execute(actor.getServer(), source, namespace, bookId, dryRun);
            if (execution.draftResult() != null && !execution.draftResult().success()) {
                return AuthorOperationResult.failure(execution.draftResult().status(),
                        execution.draftResult().code(), execution.draftResult().message(), execution);
            }
            return dryRun ? AuthorOperationResult.noChange("IMPORT_DRY_RUN", "FTB import dry-run completed", execution)
                    : AuthorOperationResult.success("FTB_DRAFT_IMPORTED", "FTB source imported as a draft", execution);
        } catch (Exception exception) {
            return AuthorOperationResult.failure(AuthorOperationResult.Status.IO_FAILURE, "FTB_IMPORT_FAILED",
                    exception.getMessage());
        }
    }

    private static <T> AuthorOperationResult<T> requireAdministrator(ServerPlayer actor) {
        if (actor == null || actor.getServer() == null
                || actor.getServer().getPlayerList().getPlayer(actor.getUUID()) != actor) {
            return AuthorOperationResult.failure(AuthorOperationResult.Status.INVALID_REQUEST,
                    "PLAYER_NOT_CONNECTED", "Editor must be connected to the target server");
        }
        if (!actor.createCommandSourceStack().hasPermission(2)) {
            return AuthorOperationResult.failure(AuthorOperationResult.Status.FORBIDDEN,
                    "EDITOR_PERMISSION_REQUIRED", "Target-server permission level 2 is required");
        }
        return null;
    }

    private static <T> AuthorOperationResult<T> failureLike(AuthorOperationResult<?> source) {
        return AuthorOperationResult.failure(source.status(), source.code(), source.message());
    }
}
