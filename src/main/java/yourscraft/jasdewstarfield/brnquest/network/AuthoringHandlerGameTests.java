package yourscraft.jasdewstarfield.brnquest.network;

import yourscraft.jasdewstarfield.brnquest.data.BookText;
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
    @GameTest(template = "empty", timeoutTicks = 600, batch = "authoringHandlerStructure")
    @PrefixGameTestTemplate(false)
    public static void structuralMutationsUseTheDecodedActionAndRevision(GameTestHelper helper) {
        var admin = helper.makeMockServerPlayerInLevel(); op(admin);
        try {
            var fixture = new MutationFixture(helper, admin);
            fixture.apply("ADD_GROUP", "g", "", "");
            fixture.apply("ADD_GROUP", "g2", "", "");
            fixture.apply("UPDATE_GROUP", "g", "", "");
            fixture.apply("MOVE_GROUP", "g2", "", "");
            fixture.apply("ADD_CHAPTER", "c", "g", "");
            fixture.apply("ADD_CHAPTER", "c2", "g2", "");
            fixture.apply("UPDATE_CHAPTER", "c", "g2", "");
            fixture.apply("MOVE_CHAPTER", "c2", "", "");
            fixture.apply("ADD_QUEST", "q", "c", "");
            fixture.apply("ADD_QUEST", "q2", "c2", "");
            fixture.apply("ADD_DEPENDENCY", "q2", "", "q");
            fixture.apply("REMOVE_DEPENDENCY", "q2", "", "q");
            fixture.apply("MOVE_QUESTS", "", "", "");
            check(helper, fixture.quest("q").x() == 12 && last(fixture.packets).action().equals("PATCH"), "position mutation emits patch");
            fixture.apply("UPDATE_QUEST_TRANSLATION", "q", "", "");
            fixture.checkQuestProperties();
            fixture.apply("COPY_QUEST", "q_copy", "", "q");
            String copiedRevision = fixture.revision;
            fixture.apply("UNDO", "", "", "");
            fixture.apply("REDO", "", "", "");
            check(helper, fixture.revision.equals(copiedRevision), "history round trip preserves exact revision");
            fixture.apply("DELETE_QUEST", "q_copy", "", "");
            fixture.apply("DELETE_CHAPTER", "c2", "", "");
            fixture.apply("DELETE_GROUP", "g", "", "");
            fixture.apply("REVIEW", "", "", "");
            check(helper, fixture.seen.size() == 18, "all structure/history actions and review executed");
            helper.succeed();
        } finally { release(admin); }
    }

    /** Small real-server fixture: the decoder and handler are the same ones used by registered payloads. */
    private static final class MutationFixture {
        private final GameTestHelper helper;
        private final ServerPlayer player;
        private final ResourceLocation book;
        private final UUID token;
        private String revision;
        private final List<CustomPacketPayload> packets = new ArrayList<>();
        private final java.util.Set<AuthoringMutationAction> seen = java.util.EnumSet.noneOf(AuthoringMutationAction.class);
        private final AuthoringResponseSender sender;
        private final AuthoringMutationHandler handler;

        MutationFixture(GameTestHelper helper, ServerPlayer player) {
            this.helper = helper; this.player = player;
            book = ResourceLocation.parse("brnquest:mutation_handler_" + player.getUUID().toString().replace("-", ""));
            var created = new DraftService().createEmpty(player, book, "Mutation fixture");
            check(helper, created.success(), created.code());
            var opened = EditSessionService.get().open(player, created.value());
            check(helper, opened.success(), opened.code());
            token = opened.value().sessionId(); revision = created.value().draftRevision();
            sender = new AuthoringResponseSender(packets::add, player.getServer()::getTickCount);
            handler = new AuthoringMutationHandler(player, sender);
        }

        ResourceLocation id(String path) { return ResourceLocation.parse("brnquest:" + path); }
        String raw(String path) { return path.isEmpty() ? "" : id(path).toString(); }
        QuestBookDefinition snapshot() { return EditSessionService.get().snapshot(player, token, book, revision).value().book(); }
        QuestDefinition quest(String path) { return snapshot().quests().stream().filter(q -> q.id().equals(id(path))).findFirst().orElseThrow(); }

        void apply(String action, String target, String parent, String source) { apply(action, target, parent, source, Map.of()); }

        void apply(String action, String target, String parent, String source, Map<String, String> config) {
            String title = action.equals("UPDATE_REWARD") ? "auto_hidden" : action.equals("UPDATE_QUEST_TRANSLATION") ? "zh_cn" : action;
            if (action.equals("UPDATE_QUEST_TRANSLATION")) config = Map.of("title", "本地化标题", "description", "描述");
            var positions = action.equals("MOVE_QUESTS") ? List.of(new AuthoringNetwork.PositionWire(raw("q"), 12, -8)) : List.<AuthoringNetwork.PositionWire>of();
            int index = action.equals("UPDATE_TASK") || action.equals("UPDATE_REWARD") ? 1 : 0;
            var wire = new AuthoringNetwork.EditorMutationWire(token.toString(), book.toString(), revision, action,
                    raw(target), raw(parent), raw(source), title, index, 1, 2, positions, config);
            var decoded = AuthoringRequestDecoder.mutation(GSON.toJson(wire));
            check(helper, decoded.success(), action + " decoded: " + decoded.failure());
            packets.clear(); handler.mutate(decoded.value());
            var response = last(packets);
            check(helper, response.status().equals("SUCCESS") || response.status().equals("NO_CHANGE"),
                    action + " result: " + response.code() + " " + response.message());
            revision = response.draftRevision(); seen.add(decoded.value().action());
            check(helper, EditSessionService.get().inspect(player, book).value().draftRevision().equals(revision), "response revision is authoritative");
        }

        void checkQuestProperties() {
            var questHandler = new AuthoringQuestUpdateHandler(player, sender);
            var wire = new AuthoringNetwork.QuestUpdateWire(token.toString(), book.toString(), revision,
                    raw("q"), raw("renamed"), "Updated", "", "", "ITEM", "minecraft:stone", true,
                    99.0, 99.0, null, null, null, null, Map.of());
            questHandler.update(AuthoringRequestDecoder.quest(GSON.toJson(wire)).value());
            check(helper, last(packets).code().equals("POSITION_WITH_RENAME"), "rename cannot simultaneously move coordinates");
            wire = new AuthoringNetwork.QuestUpdateWire(token.toString(), book.toString(), revision,
                    raw("q"), raw("q"), "Updated", "", "", "ITEM", "brnquest:unregistered_icon", false,
                    null, null, null, null, null, null, Map.of());
            questHandler.update(AuthoringRequestDecoder.quest(GSON.toJson(wire)).value());
            check(helper, last(packets).code().equals("INVALID_ICON_ITEM"), "handler checks icon registry on the server");
            check(helper, EditSessionService.get().inspect(player, book).value().draftRevision().equals(revision), "rejected properties never advance revision");
            wire = new AuthoringNetwork.QuestUpdateWire(token.toString(), book.toString(), revision,
                    raw("q"), raw("q"), "Updated", "", "", "ITEM", "minecraft:stone", true,
                    12.0, -8.0, "square", 1.0, 1.0, 0.0, Map.of("repeatable", "true"));
            var decoded = AuthoringRequestDecoder.quest(GSON.toJson(wire));
            check(helper, decoded.success(), "complete property request decoded");
            questHandler.update(decoded.value());
            check(helper, last(packets).status().equals("SUCCESS"), "complete property update succeeds");
            revision = last(packets).draftRevision();
            check(helper, quest("q").title().equals("Updated") && quest("q").behavior().repeatable(), "properties and behavior applied without losing typed entries");
            var stale = new AuthoringRequestDecoder.QuestRequest(token, book, "stale", id("q"), id("q"), "Stale", "", "",
                    null, null, true, null, null, null, null);
            questHandler.update(stale);
            check(helper, last(packets).code().equals("STALE_DRAFT_REVISION"), "property handler still defers revision to service");
            var missing = new AuthoringRequestDecoder.QuestRequest(token, book, revision, id("missing"), id("missing"), "Missing", "", "",
                    null, null, true, null, null, null, null);
            questHandler.update(missing);
            check(helper, last(packets).code().equals("QUEST_NOT_FOUND"), "disappeared quest keeps stable error");
        }
    }
    @GameTest(template = "empty", timeoutTicks = 600, batch = "authoringHandlerMutations")
    @PrefixGameTestTemplate(false)
    public static void everyMutationAndQuestUpdateUsesAuthoritativeSnapshots(GameTestHelper helper) {
        var admin = helper.makeMockServerPlayerInLevel(); op(admin);
        try {
            var fixture = new MutationFixture(helper, admin);
            fixture.apply("ADD_GROUP", "g", "", "");
            fixture.apply("ADD_GROUP", "g2", "", "");
            fixture.apply("UPDATE_GROUP", "g", "", "");
            fixture.apply("MOVE_GROUP", "g2", "", "");
            fixture.apply("ADD_CHAPTER", "c", "g", "");
            fixture.apply("ADD_CHAPTER", "c2", "g2", "");
            fixture.apply("UPDATE_CHAPTER", "c", "g2", "");
            fixture.apply("MOVE_CHAPTER", "c2", "", "");
            fixture.apply("ADD_QUEST", "q", "c", "");
            fixture.apply("ADD_QUEST", "q2", "c2", "");
            fixture.apply("ADD_DEPENDENCY", "q2", "", "q");
            check(helper, fixture.quest("q2").dependencies().contains(fixture.id("q")), "dependency added to target quest");
            fixture.apply("REMOVE_DEPENDENCY", "q2", "", "q");
            check(helper, fixture.quest("q2").dependencies().isEmpty(), "dependency removed from target quest");
            fixture.apply("MOVE_QUESTS", "", "", "");
            check(helper, last(fixture.packets).action().equals("PATCH") && fixture.quest("q").x() == 12, "movement returns revision-bound position patch");
            fixture.apply("UPDATE_QUEST_TRANSLATION", "q", "", "");
            var opaque = Map.of("extension", "keep 原样 {json:1}");
            fixture.apply("ADD_TASK", "task", "q", "opaque_task", opaque);
            fixture.apply("UPDATE_TASK", "task", "q", "task", opaque);
            fixture.apply("COPY_TASK", "task_copy", "q", "task");
            check(helper, fixture.quest("q").tasks().get(1).config().equals(opaque)
                    && fixture.quest("q").tasks().get(1).optional(), "server copy preserves unknown config and optional flag");
            fixture.apply("MOVE_TASK", "task_copy", "q", "");
            check(helper, fixture.quest("q").tasks().getFirst().id().equals(fixture.id("task_copy")), "task moved to requested index");
            fixture.apply("DELETE_TASK", "task_copy", "q", "");
            fixture.apply("ADD_REWARD", "reward", "q", "opaque_reward", opaque);
            fixture.apply("UPDATE_REWARD", "reward", "q", "reward", opaque);
            fixture.apply("COPY_REWARD", "reward_copy", "q", "reward");
            var reward = fixture.quest("q").rewards().get(1);
            check(helper, reward.config().equals(opaque) && reward.claimPolicy().equals("auto_hidden") && reward.teamReward(),
                    "server reward copy preserves unknown config, claim policy and team semantics");
            fixture.apply("MOVE_REWARD", "reward_copy", "q", "");
            check(helper, fixture.quest("q").rewards().getFirst().id().equals(fixture.id("reward_copy")), "reward moved to requested index");
            fixture.apply("DELETE_REWARD", "reward_copy", "q", "");
            fixture.checkQuestProperties();
            fixture.apply("COPY_QUEST", "q_copy", "", "q");
            check(helper, fixture.quest("q_copy").tasks().getFirst().config().equals(opaque), "quest copy uses server-owned nested task data");
            // Exercise translation copying through the actual decoder, handler and draft transaction.
            check(helper, BookText.quest(fixture.snapshot(),
                    fixture.quest("q_copy"), "zh_cn", "title", "").equals("本地化标题"),
                    "quest copy preserves server-owned localized text");
            String beforeUndo = fixture.revision;
            fixture.apply("UNDO", "", "", "");
            check(helper, fixture.snapshot().quests().stream().noneMatch(q -> q.id().equals(fixture.id("q_copy"))), "undo removes copied quest");
            fixture.apply("REDO", "", "", "");
            check(helper, fixture.revision.equals(beforeUndo), "redo restores the exact semantic revision");
            fixture.apply("DELETE_QUEST", "q_copy", "", "");
            fixture.apply("DELETE_CHAPTER", "c2", "", "");
            check(helper, fixture.snapshot().quests().stream().noneMatch(q -> q.id().equals(fixture.id("q2"))), "chapter deletion removes its contained quest");
            fixture.apply("DELETE_GROUP", "g", "", "");
            fixture.apply("REVIEW", "", "", "");
            // Review mirrors the service validation and revision gates, even before an explicit save.
            var preview = AuthorApi.previewPublish(admin, fixture.token, fixture.book, fixture.revision);
            check(helper, last(fixture.packets).review() != null
                    && last(fixture.packets).review().publishAllowed() == preview.success(),
                    "review preserves the authoritative publish decision");
            check(helper, fixture.seen.size() == AuthoringMutationAction.values().length, "all 27 mutations plus review executed");
            helper.succeed();
        } finally { release(admin); }
    }

}
