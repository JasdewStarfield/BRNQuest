package yourscraft.jasdewstarfield.brnquest.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.api.AuthorApi;
import yourscraft.jasdewstarfield.brnquest.author.*;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;
import java.util.UUID;

/** Server-authoritative authoring use cases; requests are parsed before entering this boundary. */
final class AuthoringSessionHandler {
    private final ServerPlayer player;
    private final AuthoringResponseSender responses;

    AuthoringSessionHandler(ServerPlayer player) { this(player, AuthoringResponseSender.forPlayer(player)); }

    /** Tests may capture the actual response stream while services still use a real server player. */
    AuthoringSessionHandler(ServerPlayer player, AuthoringResponseSender responses) {
        this.player = player;
        this.responses = responses;
    }

    void openLive(AuthoringRequestDecoder.OpenRequest request) {
        var bookId = request.bookId();
        var opened = EditSessionService.get().openLive(player, bookId);
        if (!opened.success()) {
            responses.sendFailure("OPEN", opened);
            return;
        }
        var draft = EditSessionService.get().snapshot(player, opened.value().sessionId(), bookId,
                opened.value().session().draftRevision());
        if (!draft.success()) {
            responses.sendFailure("OPEN", draft);
            return;
        }
        sendDraft("OPEN", "SESSION_LIVE_OPENED", "Live editing", opened.value(), draft.value());
    }

    void sendCatalog() {
        responses.sendCatalog(AuthorApi.catalog(player));
    }

    void sendDraftVersions(ResourceLocation bookId) {
        responses.sendDraftVersions(bookId, new yourscraft.jasdewstarfield.brnquest.author.AuthorBackupService()
                .listDraftVersions(player, bookId));
    }

    void versionSavedDraft(AuthoringRequestDecoder.DraftVersionRequest request) {
        var versioned = new DraftService().versionSavedDraft(player, request.bookId(),
                request.expectedDraftRevision());
        if (!versioned.success()) {
            responses.sendFailure("OPEN", versioned);
            return;
        }
        sendCatalog();
        open(new AuthoringRequestDecoder.OpenRequest(request.bookId(), versioned.value().draftRevision()),
                "DRAFT_VERSION_CREATED", "Saved draft copied into history and reopened");
    }

    void restoreDraftVersion(AuthoringRequestDecoder.DraftRestoreRequest request) {
        var restored = new yourscraft.jasdewstarfield.brnquest.author.AuthorBackupService()
                .restoreDraftVersion(player, request.bookId(), request.backupId(),
                        request.expectedCurrentRevision());
        if (!restored.success()) {
            responses.sendFailure("OPEN", restored);
            return;
        }
        sendCatalog();
        var draft = new DraftRepository().load(player.getServer(), request.bookId());
        if (!draft.success()) {
            responses.sendFailure("OPEN", draft);
            return;
        }
        open(new AuthoringRequestDecoder.OpenRequest(request.bookId(), draft.value().draftRevision()),
                "DRAFT_BACKUP_RESTORED", "Saved draft version restored and opened");
    }

    void open(AuthoringRequestDecoder.OpenRequest request) {
        open(request, "SESSION_OPENED", "Edit session opened");
    }

    private void open(AuthoringRequestDecoder.OpenRequest request, String code, String message) {
        ResourceLocation bookId = request.bookId();
        String expectedDraftRevision = request.expectedDraftRevision();
        AuthorOperationResult<EditSessionHandle> opened = AuthorApi.open(player, bookId, expectedDraftRevision);
        if (!opened.success()) {
            responses.sendFailure("OPEN", opened);
            return;
        }
        EditSessionHandle handle = opened.value();
        AuthorOperationResult<DraftSnapshot> snapshot = EditSessionService.get().snapshot(player,
                handle.sessionId(), bookId, handle.session().draftRevision());
        if (!snapshot.success()) {
            AuthorApi.close(player, handle.sessionId(), handle.session().draftRevision());
            responses.sendFailure("OPEN", snapshot);
            return;
        }
        sendDraft("OPEN", code, message, handle, snapshot.value());
    }

    void openCurrent(AuthoringRequestDecoder.CurrentRequest payload) {
        ResourceLocation bookId = payload.bookId();
        var active = QuestBookManager.get().active().orElse(null);
        if (bookId == null || active == null || !bookId.equals(active.book().id())) {
            responses.sendFailure("OPEN", AuthorOperationResult.Status.INVALID_REQUEST,
                    "ACTIVE_BOOK_CHANGED", "The displayed task book is no longer active on this server");
            return;
        }
        if (!active.revision().equals(payload.activeRevision())) {
            responses.sendFailure("OPEN", AuthorOperationResult.Status.CONFLICT,
                    "ACTIVE_BOOK_CHANGED", "The active task book changed after the draft choice was shown");
            return;
        }
        if (payload.replaceDraft()) {
            AuthorOperationResult<DraftSnapshot> replaced = new DraftService().replaceFromActive(player,
                    payload.draftRevision());
            if (!replaced.success()) {
                responses.sendFailure("OPEN", replaced);
                return;
            }
        } else {
            if (payload.draftRevision().isBlank()) {
                AuthorOperationResult<DraftSnapshot> created = AuthorApi.createFromActive(player);
                if (!created.success()) {
                    responses.sendFailure("OPEN", created);
                    return;
                }
            }
        }
        sendCatalog();
        // Continuing and replacing both pass through the normal permission,
        // ownership, migration, revision, and lease checks below.
        String openedCode = "SESSION_OPENED";
        String openedMessage = "Edit session opened";
        if (payload.replaceDraft()) {
            boolean hadDraft = !payload.draftRevision().isBlank();
            openedCode = hadDraft ? "DRAFT_VERSION_CREATED" : "DRAFT_COPY_CREATED";
            openedMessage = hadDraft ? "Current task book copied; previous draft backed up"
                    : "Current task book copied into a new draft";
        }
        open(new AuthoringRequestDecoder.OpenRequest(bookId, payload.replaceDraft() ? "" :
                payload.draftRevision().isBlank() ? active.revision() : payload.draftRevision()),
                openedCode, openedMessage);
    }

    void renew(AuthoringRequestDecoder.LeaseRequest request) {
        UUID sessionId = request.sessionId();
        String draftRevision = request.draftRevision();
        AuthorOperationResult<EditSessionHandle> result = AuthorApi.renew(player, sessionId, draftRevision);
        if (!result.success()) {
            responses.sendFailure("RENEW", result);
            return;
        }
        responses.sendSession("RENEW", result.status(), result.code(), result.message(), result.value(), 0, 0);
    }

    void close(AuthoringRequestDecoder.LeaseRequest request) {
        UUID sessionId = request.sessionId();
        String draftRevision = request.draftRevision();
        AuthorOperationResult<EditSessionView> result = AuthorApi.close(player, sessionId, draftRevision);
        if (!result.success()) {
            responses.sendFailure("CLOSE", result);
            return;
        }
        EditSessionView view = result.value();
        EditSessionHandle handle = new EditSessionHandle(sessionId, view);
        responses.sendSession("CLOSE", result.status(), result.code(), result.message(), handle, 0, 0);
    }

    void recover(AuthoringRequestDecoder.RecoveryRequest wire) {
        UUID sessionId = wire.sessionId();
        ResourceLocation bookId = wire.bookId();
        if (wire.action() == AuthoringRequestDecoder.RecoveryAction.ABANDON) {
            var abandoned = EditSessionService.get().abandon(player, sessionId, bookId);
            if (!abandoned.success()) {
                responses.sendFailure("RECOVER", abandoned);
                return;
            }
            responses.sendSession("CLOSE", abandoned.status(), abandoned.code(), abandoned.message(),
                    new EditSessionHandle(sessionId, abandoned.value()), 0, 0);
            return;
        }
        var recovered = EditSessionService.get().recover(player, sessionId, bookId);
        if (!recovered.success()) {
            responses.sendFailure("RECOVER", recovered);
            return;
        }
        var snapshot = EditSessionService.get().snapshot(player, sessionId, bookId,
                recovered.value().session().draftRevision());
        if (!snapshot.success()) {
            responses.sendFailure("RECOVER", snapshot);
            return;
        }
        if (wire.action() == AuthoringRequestDecoder.RecoveryAction.REFRESH) {
            sendDraft("RECOVER", "SESSION_RECOVERED", "Authoritative draft re-synchronized",
                    recovered.value(), snapshot.value());
            return;
        }
        ResourceLocation target = wire.targetBookId();
        var copied = new yourscraft.jasdewstarfield.brnquest.author.DraftService()
                .createRecoveryCopy(player, snapshot.value(), target);
        if (!copied.success()) {
            responses.sendFailure("RECOVER", copied);
            return;
        }
        var abandoned = EditSessionService.get().abandon(player, sessionId, bookId);
        if (!abandoned.success()) {
            responses.sendFailure("RECOVER", abandoned);
            return;
        }
        var opened = EditSessionService.get().open(player, copied.value());
        if (!opened.success()) {
            responses.sendFailure("RECOVER", opened);
            return;
        }
        sendDraft("RECOVER", "RECOVERY_COPY_OPENED", "Recovery copy created and opened",
                opened.value(), copied.value());
    }

    /** Closing an oversized session is a use-case decision supplied to the transport explicitly. */
    private void sendDraft(String action, String code, String message,
                                  EditSessionHandle handle, DraftSnapshot draft) {
        responses.sendDraft(action, code, message, handle, draft,
                () -> AuthorApi.close(player, handle.sessionId(), handle.session().draftRevision()));
    }
}
