package yourscraft.jasdewstarfield.brnquest.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.api.AuthorApi;
import java.util.UUID;
import yourscraft.jasdewstarfield.brnquest.BRNQuest;
import yourscraft.jasdewstarfield.brnquest.author.AuthorOperationResult;

/** Server-authoritative authoring use cases; requests are parsed before entering this boundary. */
final class AuthoringPublicationHandler {
    private final ServerPlayer player;
    private final AuthoringResponseSender responses;

    AuthoringPublicationHandler(ServerPlayer player) { this(player, AuthoringResponseSender.forPlayer(player)); }

    /** Captures real responses in server workflow tests without replacing the authoritative services. */
    AuthoringPublicationHandler(ServerPlayer player, AuthoringResponseSender responses) {
        this.player = player;
        this.responses = responses;
    }

    void save(AuthoringRequestDecoder.SessionRequest request) {
        UUID sessionId = request.sessionId();
        ResourceLocation bookId = request.bookId();
        String draftRevision = request.draftRevision();
        var saved = AuthorApi.save(player, sessionId, bookId, draftRevision);
        if (!saved.success()) {
            responses.sendSaveFailure(saved);
            return;
        }
        var renewed = AuthorApi.renew(player, sessionId, draftRevision);
        if (!renewed.success()) {
            responses.sendFailure("SAVE", renewed.status(), renewed.code(), renewed.message());
            return;
        }
        responses.sendSession("SAVE", saved.status(), saved.code(), saved.message(), renewed.value(), 0, 0);
    }

    void review(AuthoringRequestDecoder.SessionRequest request) {
        UUID sessionId = request.sessionId();
        ResourceLocation bookId = request.bookId();
        String draftRevision = request.draftRevision();
        var preview = AuthorApi.previewPublish(player, sessionId, bookId, draftRevision);
        if (!preview.success() && preview.value() == null) {
            responses.sendFailure("REVIEW", preview.status(), preview.code(), preview.message());
            return;
        }
        var diff = AuthorApi.diff(player, sessionId, bookId, draftRevision,
                yourscraft.jasdewstarfield.brnquest.author.DraftDiffService.Baseline.WORKSPACE);
        if (!diff.success() || diff.value() == null) {
            responses.sendFailure("REVIEW", diff.status(), diff.code(), diff.message());
            return;
        }
        var renewed = AuthorApi.renew(player, sessionId, draftRevision);
        if (!renewed.success()) {
            responses.sendFailure("REVIEW", renewed.status(), renewed.code(), renewed.message());
            return;
        }
        responses.sendPublishReview(renewed.value(), preview.success(), bookId,
                preview.value(), diff.value());
    }

    void publishAndApply(AuthoringRequestDecoder.SessionRequest request) {
        UUID sessionId = request.sessionId();
        ResourceLocation bookId = request.bookId();
        String draftRevision = request.draftRevision();
        debugPublishPhase(player, bookId, draftRevision, "request", "STARTED", "PUBLISH_REQUEST_ACCEPTED");
        var saved = AuthorApi.save(player, sessionId, bookId, draftRevision);
        debugPublishPhase(player, bookId, draftRevision, "save", saved.status().name(), saved.code());
        if (!saved.success()) {
            responses.sendFailure("PUBLISH", saved.status(), saved.code(), "Save failed: " + saved.message());
            return;
        }
        var published = AuthorApi.publish(player, sessionId, bookId, draftRevision);
        debugPublishPhase(player, bookId, draftRevision, "workspace", published.status().name(), published.code());
        if (!published.success()) {
            String message = "Draft was saved, but publish failed: " + published.message();
            if (published.value() != null && published.value().revisionCheck() != null
                    && published.value().revisionCheck().hasConflicts()) {
                var first = published.value().revisionCheck().conflicts().getFirst();
                message += ": expected " + AuthoringResponseSender.shortRevision(first.expectedRevision())
                        + ", actual " + AuthoringResponseSender.shortRevision(first.actualRevision());
            } else if (published.value() != null && !published.value().diagnostics().isEmpty()) {
                var first = published.value().diagnostics().getFirst();
                message += ": " + first.code() + " " + first.objectId() + " — " + first.message();
            }
            responses.sendFailure("PUBLISH", published.status(), published.code(),
                    message);
            return;
        }
        var deployed = AuthorApi.deploy(player, true);
        debugPublishPhase(player, bookId, draftRevision, "deploy", deployed.status().name(), deployed.code());
        if (!deployed.success()) {
            responses.sendFailure("PUBLISH", deployed.status(), deployed.code(),
                    "Workspace publish completed, but deployment failed: " + deployed.message());
            return;
        }
        var renewed = AuthorApi.renew(player, sessionId, draftRevision);
        debugPublishPhase(player, bookId, draftRevision, "renew", renewed.status().name(), renewed.code());
        if (!renewed.success()) {
            responses.sendFailure("PUBLISH", renewed.status(), renewed.code(),
                    "Workspace was deployed, but the edit lease could not be renewed: " + renewed.message());
            return;
        }
        AuthorApi.reload(player).whenComplete((reloaded, error) -> player.getServer().execute(() -> {
            if (error != null) {
                debugPublishPhase(player, bookId, draftRevision, "reload", "IO_FAILURE", "RELOAD_FAILED");
                responses.sendFailure("PUBLISH", AuthorOperationResult.Status.IO_FAILURE, "RELOAD_FAILED",
                        "Workspace was deployed, but reload failed: " + error.getMessage());
            } else if (!reloaded.success()) {
                debugPublishPhase(player, bookId, draftRevision, "reload",
                        reloaded.status().name(), reloaded.code());
                responses.sendFailure("PUBLISH", reloaded.status(), reloaded.code(),
                        "Workspace was deployed, but reload failed: " + reloaded.message());
            } else {
                debugPublishPhase(player, bookId, draftRevision, "reload",
                        reloaded.status().name(), "PUBLISH_APPLY_COMPLETE");
                responses.sendSession("PUBLISH", AuthorOperationResult.Status.SUCCESS, "PUBLISH_APPLY_COMPLETE",
                        "Draft published, deployed with backup, and reloaded", renewed.value(), 0, 0);
            }
        }));
    }

    private static void debugPublishPhase(ServerPlayer player, ResourceLocation bookId, String revision,
                                          String phase, String status, String code) {
        BRNQuest.LOGGER.debug("[BRNQuest/EDITOR] actor={} book={} revision={} phase={} status={} code={}",
                player.getGameProfile().getName(), bookId, AuthoringResponseSender.shortRevision(revision), phase, status, code);
    }

}
