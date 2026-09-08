package yourscraft.jasdewstarfield.brnquest.network;

import com.google.gson.Gson;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import yourscraft.jasdewstarfield.brnquest.api.AuthorApi;
import yourscraft.jasdewstarfield.brnquest.author.*;
import yourscraft.jasdewstarfield.brnquest.data.*;
import yourscraft.jasdewstarfield.brnquest.diagnostic.DiagnosticReport;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Real server use cases with captured response packets; the isolated GameTest run owns all fixture state. */
@GameTestHolder("brnquest")
public final class AuthoringHandlerGameTests {
    private static final Gson GSON = new Gson();
    private AuthoringHandlerGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 400, batch = "authoringHandlerSessions")
    @PrefixGameTestTemplate(false)
    public static void sessionRecoveryAndSaveKeepServerAuthority(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var admin = helper.makeMockServerPlayerInLevel();
        var rival = helper.makeMockServerPlayerInLevel();
        var ordinary = helper.makeMockServerPlayerInLevel();
        op(admin); op(rival);
        var packets = new ArrayList<CustomPacketPayload>();
        var sender = new AuthoringResponseSender(packets::add, server::getTickCount);
        var session = new AuthoringSessionHandler(admin, sender);
        var publication = new AuthoringPublicationHandler(admin, sender);
        var book = ResourceLocation.parse("brnquest:handler_" + admin.getUUID().toString().replace("-", ""));
        try {
            var created = new DraftService().createEmpty(admin, book, "Handler fixture");
            check(helper, created.success(), created.code());
            var request = new AuthoringRequestDecoder.OpenRequest(book, "");
            new AuthoringSessionHandler(ordinary, sender).open(request);
            check(helper, last(packets).status().equals("FORBIDDEN"), "ordinary players rejected by service");
            packets.clear();
            session.open(request);
            var opened = last(packets);
            check(helper, opened.action().equals("OPEN") && opened.chunks() > 0, "session metadata with draft chunks");
            check(helper, packets.getFirst() instanceof AuthoringNetwork.SessionPayload, "metadata is sent first");
            UUID token = UUID.fromString(opened.sessionId());
            new AuthoringSessionHandler(rival, sender).open(request);
            check(helper, last(packets).code().equals("BOOK_ALREADY_EDITED"), "other administrator cannot steal lease");
            session.renew(new AuthoringRequestDecoder.LeaseRequest(token, "stale"));
            check(helper, last(packets).code().equals("STALE_DRAFT_REVISION"), "old revision rejected");
            var edited = new DraftEditService().setBookTitle(admin, token, book, opened.draftRevision(), "Changed by handler test");
            check(helper, edited.success(), edited.code());
            String revision = edited.value().snapshot().draftRevision();
            publication.save(new AuthoringRequestDecoder.SessionRequest(token, book, opened.draftRevision()));
            check(helper, last(packets).code().equals("STALE_DRAFT_REVISION"), "stale save must not advance saved revision");
            check(helper, EditSessionService.get().inspect(admin, book).value().dirty(), "failed save remains dirty");
            publication.save(new AuthoringRequestDecoder.SessionRequest(token, book, revision));
            check(helper, last(packets).savedRevision().equals(revision), "save renews metadata with saved revision");
            publication.save(new AuthoringRequestDecoder.SessionRequest(token, book, revision));
            check(helper, last(packets).status().equals("NO_CHANGE"), "repeated save is idempotent");
            session.recover(new AuthoringRequestDecoder.RecoveryRequest(token, book,
                    AuthoringRequestDecoder.RecoveryAction.REFRESH, null));
            check(helper, last(packets).code().equals("SESSION_RECOVERED"), "refresh returns authoritative draft");
            var copy = ResourceLocation.parse(book + "_copy");
            session.recover(new AuthoringRequestDecoder.RecoveryRequest(token, book,
                    AuthoringRequestDecoder.RecoveryAction.SAVE_AS, copy));
            var recovered = last(packets);
            check(helper, recovered.code().equals("RECOVERY_COPY_OPENED") && recovered.bookId().equals(copy.toString()),
                    "save-as opens a separate recovery copy");
            session.recover(new AuthoringRequestDecoder.RecoveryRequest(UUID.fromString(recovered.sessionId()), copy,
                    AuthoringRequestDecoder.RecoveryAction.ABANDON, null));
            check(helper, last(packets).action().equals("CLOSE"), "abandon closes the recovered lease");
            session.open(request);
            var reopened = last(packets);
            session.close(new AuthoringRequestDecoder.LeaseRequest(UUID.fromString(reopened.sessionId()), reopened.draftRevision()));
            check(helper, last(packets).action().equals("CLOSE"), "normal close remains usable after recovery");
            helper.succeed();
        } finally { release(admin); release(rival); release(ordinary); }
    }

    @GameTest(template = "empty", timeoutTicks = 400, batch = "authoringHandlerCurrent")
    @PrefixGameTestTemplate(false)
    public static void currentAndLiveRequestsKeepSeparateSources(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var admin = helper.makeMockServerPlayerInLevel(); op(admin);
        var manager = QuestBookManager.get();
        var previous = manager.active().orElseThrow();
        var source = manager.activeResource().orElse(null);
        var bookId = ResourceLocation.parse("brnquest:current_handler_" + admin.getUUID().toString().replace("-", ""));
        var book = new QuestBookDefinition(bookId, 1, "Active fixture", List.of(), List.of(), Map.of());
        var packets = new ArrayList<CustomPacketPayload>();
        var session = new AuthoringSessionHandler(admin, new AuthoringResponseSender(packets::add, server::getTickCount));
        try {
            check(helper, manager.install(book, new DiagnosticReport()), "install isolated active fixture");
            String revision = manager.active().orElseThrow().revision();
            session.openCurrent(new AuthoringRequestDecoder.CurrentRequest(bookId, "old", "", false));
            check(helper, last(packets).code().equals("ACTIVE_BOOK_CHANGED"), "stale active selection rejected");
            session.openCurrent(new AuthoringRequestDecoder.CurrentRequest(bookId, revision, "", false));
            var opened = last(packets);
            check(helper, opened.code().equals("SESSION_OPENED"), "current creates advanced draft and opens it");
            session.close(new AuthoringRequestDecoder.LeaseRequest(UUID.fromString(opened.sessionId()), opened.draftRevision()));
            session.openCurrent(new AuthoringRequestDecoder.CurrentRequest(bookId, revision, opened.draftRevision(), true));
            opened = last(packets);
            check(helper, opened.code().equals("SESSION_OPENED"), "replace uses expected advanced draft revision");
            session.close(new AuthoringRequestDecoder.LeaseRequest(UUID.fromString(opened.sessionId()), opened.draftRevision()));
            session.openLive(new AuthoringRequestDecoder.OpenRequest(bookId, ""));
            opened = last(packets);
            check(helper, opened.code().equals("SESSION_LIVE_OPENED"), "live opens from active source");
            session.open(new AuthoringRequestDecoder.OpenRequest(bookId, ""));
            check(helper, last(packets).code().equals("BOOK_ALREADY_EDITED"), "advanced source cannot reuse live lease");
            helper.succeed();
        } finally {
            release(admin);
            manager.install(previous.book(), new DiagnosticReport(), source);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 600, batch = "authoringHandlerPublication")
    @PrefixGameTestTemplate(false)
    public static void publicationReviewAndApplyKeepStageFailures(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var admin = helper.makeMockServerPlayerInLevel(); op(admin);
        var packets = new ArrayList<CustomPacketPayload>();
        var sender = new AuthoringResponseSender(packets::add, server::getTickCount);
        var publication = new AuthoringPublicationHandler(admin, sender);
        var sessions = EditSessionService.get();
        var manager = QuestBookManager.get();
        var previous = manager.active().orElseThrow();
        var previousSource = manager.activeResource().orElse(null);
        var bookId = ResourceLocation.parse("brnquest:publish_handler_" + admin.getUUID().toString().replace("-", ""));
        Runnable cleanup = () -> {
            release(admin);
            manager.install(previous.book(), new DiagnosticReport(), previousSource);
        };
        try {
            var created = new DraftService().createEmpty(admin, bookId, "Publication handler fixture");
            check(helper, created.success(), created.code());
            var opened = sessions.open(admin, created.value());
            check(helper, opened.success(), opened.code());
            var request = new AuthoringRequestDecoder.SessionRequest(opened.value().sessionId(), bookId,
                    created.value().draftRevision());
            publication.publishAndApply(new AuthoringRequestDecoder.SessionRequest(request.sessionId(), bookId, "stale"));
            check(helper, last(packets).code().equals("STALE_DRAFT_REVISION")
                    && last(packets).message().startsWith("Save failed:"), "failed save cannot advance publication");
            publication.review(request);
            check(helper, last(packets).review() != null && last(packets).review().publishAllowed(), "saved draft review uses real publish gates");
            String active = manager.active().orElseThrow().revision();
            check(helper, active.equals(previous.revision()), "review does not reload or install drafts");
            packets.clear();
            publication.publishAndApply(request);
            // Reload is asynchronous; wait for the actual final response, not just disk or log evidence.
            helper.startSequence().thenWaitUntil(() -> {
                check(helper, !packets.isEmpty(), "await publish response");
                var response = last(packets);
                check(helper, response.code().equals("PUBLISH_APPLY_COMPLETE"),
                        "publication stages: " + response.code() + " " + response.message());
                check(helper, response.savedRevision().equals(request.draftRevision()), "success preserves saved revision");
            }).thenExecute(cleanup).thenSucceed();
        } catch (Throwable failure) {
            cleanup.run();
            throw failure;
        }
    }

    private static AuthoringNetwork.SessionResponseWire last(List<CustomPacketPayload> packets) {
        for (int i = packets.size() - 1; i >= 0; i--) {
            if (packets.get(i) instanceof AuthoringNetwork.SessionPayload payload)
                return GSON.fromJson(payload.json(), AuthoringNetwork.SessionResponseWire.class);
        }
        throw new AssertionError("No session response was delivered");
    }

    private static void check(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, message);
    }

    private static void op(ServerPlayer player) {
        var players = player.getServer().getPlayerList();
        players.getOps().add(new ServerOpListEntry(player.getGameProfile(), 2, false));
        players.sendPlayerPermissionLevel(player);
    }

    @SuppressWarnings("removal")
    private static void release(ServerPlayer player) {
        EditSessionService.get().releasePlayer(player.getServer(), player.getUUID());
        var players = player.getServer().getPlayerList();
        players.getOps().remove(player.getGameProfile());
        if (players.getPlayer(player.getUUID()) == player) players.remove(player);
    }
}
