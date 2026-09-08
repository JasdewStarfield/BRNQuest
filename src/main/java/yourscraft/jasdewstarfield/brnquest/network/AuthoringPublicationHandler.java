package yourscraft.jasdewstarfield.brnquest.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.api.AuthorApi;
import java.util.UUID;

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

}
