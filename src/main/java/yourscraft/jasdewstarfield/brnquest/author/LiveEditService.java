package yourscraft.jasdewstarfield.brnquest.author;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.WorldDataConfiguration;
import yourscraft.jasdewstarfield.brnquest.data.QuestBookSnapshot;
import yourscraft.jasdewstarfield.brnquest.diagnostic.DiagnosticReport;
import yourscraft.jasdewstarfield.brnquest.progress.ProgressEngine;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;
import yourscraft.jasdewstarfield.brnquest.workspace.WorkspacePaths;

import java.util.ArrayList;
import java.util.List;

/** Server-only commit boundary for complete lightweight editor operations. */
final class LiveEditService {
    private LiveEditService() {}

    static AuthorOperationResult<DraftEditResult> commit(MinecraftServer server, ServerPlayer actor,
                                                         DraftSnapshot previous, DraftEditResult candidate) {
        var manager = QuestBookManager.get();
        var active = manager.active().orElse(null);
        if (active == null || !active.book().id().equals(previous.book().id())
                || !active.revision().equals(previous.draftRevision())) {
            return AuthorOperationResult.failure(AuthorOperationResult.Status.CONFLICT,
                    "LIVE_BOOK_CHANGED", "Active book changed; close and reopen live editing");
        }
        var book = candidate.snapshot().book();
        var diagnostics = AuthorValidationService.full(book);
        if (AuthorValidationService.blocksCommit(diagnostics, AuthorValidationService.full(active.book()))) {
            return AuthorOperationResult.failure(AuthorOperationResult.Status.INVALID_REQUEST,
                    "DRAFT_VALIDATION_FAILED", "Edit validation failed; the active book was not changed", candidate);
        }
        if (candidate.snapshot().draftRevision().equals(previous.draftRevision())) {
            return AuthorOperationResult.noChange("LIVE_UNCHANGED", "No change", candidate);
        }
        try {
            LiveBookRepository.preparePack(WorkspacePaths.deployed(server));
            enablePack(server);
            LiveBookRepository.save(WorkspacePaths.deployed(server),
                    server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("brnquest-backups/live"),
                    previous.draftRevision(), book, manager.activeResource().orElse(book.id()));
        } catch (Exception exception) {
            var failure = AuthorOperationResult.<DraftEditResult>failure(AuthorOperationResult.Status.IO_FAILURE,
                    "LIVE_SAVE_FAILED", "Could not save world edits: " + exception.getMessage());
            AuthorAuditLog.record(actor, "live_edit", book.id().toString(), previous.draftRevision(), previous.draftRevision(), failure);
            return failure;
        }
        DiagnosticReport report = new DiagnosticReport();
        diagnostics.forEach(report::add);
        manager.installLiveValidated(QuestBookSnapshot.of(book), report);
        ProgressEngine.get().reconcileOnlinePlayers(server);
        DraftSnapshot snapshot = DraftSnapshot.from(book, DraftOrigin.ACTIVE, candidate.snapshot().draftRevision());
        var result = AuthorOperationResult.success("LIVE_SAVED", "Saved and applied",
                new DraftEditResult(snapshot, candidate.affectedObjects(), diagnostics));
        AuthorAuditLog.record(actor, "live_edit", book.id().toString(), previous.draftRevision(), snapshot.draftRevision(), result);
        return result;
    }

    /** Select before committing: a discovery failure must not leave a new file behind an old runtime snapshot. */
    private static void enablePack(MinecraftServer server) throws java.io.IOException {
        var repository = server.getPackRepository();
        var config = server.getWorldData().getDataConfiguration();
        if (!repository.getSelectedIds().contains(WorkspacePaths.PACK_ID)) {
            repository.reload();
            if (!repository.getAvailableIds().contains(WorkspacePaths.PACK_ID)) {
                throw new java.io.IOException("World edit pack could not be discovered");
            }
            var selected = new ArrayList<>(repository.getSelectedIds());
            selected.remove(WorkspacePaths.PACK_ID);
            selected.add(WorkspacePaths.PACK_ID);
            repository.setSelected(selected);
            server.getWorldData().setDataConfiguration(new WorldDataConfiguration(new DataPackConfig(
                    List.copyOf(selected), config.dataPacks().getDisabled().stream()
                    .filter(id -> !id.equals(WorkspacePaths.PACK_ID)).toList()), config.enabledFeatures()));
        }
    }
}
