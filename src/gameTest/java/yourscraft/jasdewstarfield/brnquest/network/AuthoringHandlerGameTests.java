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
            check(helper, created.success(), created.code() + ": " + created.message());
            var opened = EditSessionService.get().open(player, created.value());
            check(helper, opened.success(), opened.code());
            token = opened.value().sessionId(); revision = created.value().draftRevision();
            sender = new AuthoringResponseSender(packets::add, player.getServer()::getTickCount);
            handler = new AuthoringMutationHandler(player, sender);
        }

        ResourceLocation id(String path) { return ResourceLocation.parse("brnquest:" + path); }
        // Explicit namespaced values let the same fixture exercise independently registered add-on types.
        String raw(String path) { return path.isEmpty() || path.contains(":") ? path : id(path).toString(); }
        QuestBookDefinition snapshot() { return EditSessionService.get().snapshot(player, token, book, revision).value().book(); }
        QuestDefinition quest(String path) { return snapshot().quests().stream().filter(q -> q.id().equals(id(path))).findFirst().orElseThrow(); }

        void apply(String action, String target, String parent, String source) { apply(action, target, parent, source, Map.of()); }

        void apply(String action, String target, String parent, String source, Map<String, String> config) {
            String title = action.equals("ADD_REWARD") ? "" : action.equals("UPDATE_REWARD") ? "auto_hidden" : action.equals("UPDATE_QUEST_TRANSLATION") ? "zh_cn" : action;
            if (action.equals("UPDATE_QUEST_TRANSLATION") && config.isEmpty()) config = Map.of(
                    "title", "本地化标题", "description", "**描述**", "description_format", "markdown_v1");
            var positions = (action.equals("MOVE_QUESTS") || action.equals("COPY_QUESTS") || action.equals("DELETE_QUESTS")) ? List.of(new AuthoringNetwork.PositionWire(raw("q"), 12, -8)) : List.<AuthoringNetwork.PositionWire>of();
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

        void selection(String action, List<String> paths) {
            var positions = paths.stream().map(path -> new AuthoringNetwork.PositionWire(raw(path), 0, 0)).toList();
            var wire = new AuthoringNetwork.EditorMutationWire(token.toString(), book.toString(), revision, action,
                    "", "", "", "", 0, 1, 1, positions, Map.of());
            var decoded = AuthoringRequestDecoder.mutation(GSON.toJson(wire));
            check(helper, decoded.success(), "selection request decoded");
            packets.clear(); handler.mutate(decoded.value());
            check(helper, last(packets).status().equals("SUCCESS"), "selection mutation succeeds: " + last(packets).code());
            revision = last(packets).draftRevision();
        }

        /** Typed positions run through the public decoder and real server transaction, not a local shortcut. */
        void canvasSelection(String action, String chapter, List<AuthoringNetwork.PositionWire> positions) {
            var wire = new AuthoringNetwork.EditorMutationWire(token.toString(), book.toString(), revision, action,
                    raw(chapter), "", "", "", 0, 1, 1, positions, Map.of());
            var decoded = AuthoringRequestDecoder.mutation(GSON.toJson(wire));
            check(helper, decoded.success(), "mixed selection decoded");
            packets.clear(); handler.mutate(decoded.value());
            check(helper, last(packets).status().equals("SUCCESS"), "mixed selection succeeds: " + last(packets).code());
            revision = last(packets).draftRevision(); seen.add(decoded.value().action());
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
    /** Real wire requests and direct author APIs share type normalization, history and disk persistence. */
    @GameTest(template = "empty", timeoutTicks = 600, batch = "authoringNormalization")
    @PrefixGameTestTemplate(false)
    public static void registryNormalizationAndExternalTypesShareAuthorTransactions(GameTestHelper helper) {
        if (!net.neoforged.fml.ModList.get().isLoaded("brnquest_example")) { helper.succeed(); return; }
        var admin = helper.makeMockServerPlayerInLevel(); op(admin);
        try {
            var f = new MutationFixture(helper, admin);
            f.apply("ADD_GROUP", "g", "", ""); f.apply("ADD_CHAPTER", "c", "g", ""); f.apply("ADD_QUEST", "q", "c", "");
            f.apply("ADD_TASK", "confirm", "q", "brnquest_example:checkmark", Map.of("title", "  Confirm  ", "opaque", "keep"));
            f.apply("ADD_REWARD", "experience", "q", "brnquest_example:experience", Map.of("amount", " 007 ", "opaque", "keep"));
            check(helper, f.quest("q").tasks().getFirst().config().get("title").equals("Confirm"), "external task normalized through wire path");
            check(helper, f.quest("q").rewards().getFirst().config().get("amount").equals("7"), "external reward normalized through wire path");
            var legacy = Map.of("item", "{id:\"minecraft:stone\",count:3,components:{\"minecraft:custom_name\":'\"Keepsake\"'}}",
                    "count", "2", "consume", "true", "private_ref", "brnquest:q");
            // Bypass the network on purpose: this used to miss registry normalization entirely.
            var added = AuthorApi.editor().addTask(admin, f.token, f.book, f.revision, f.id("q"),
                    new TaskDefinition(f.book, f.id("item"), ResourceLocation.parse("brnquest:item"), legacy, false));
            check(helper, added.success(), "direct API item insertion: " + added.message());
            f.revision = added.value().snapshot().draftRevision();
            var canonical = f.quest("q").tasks().get(1).config();
            check(helper, canonical.get("matcher").contains("Keepsake") && canonical.get("consume_items").equals("true"),
                    "live registry normalization preserves item components and legacy consume");
            f.apply("ADD_TASK", "choice", "q", "item_choice", Map.of("matcher", "{\"mode\":\"tag\",\"tag\":\"minecraft:planks\"}", "opaque", "keep"));
            f.apply("COPY_TASK", "item_copy", "q", "item");
            check(helper, f.quest("q").tasks().getLast().config().equals(canonical), "individual copy normalizes idempotently");
            String beforeCopy = f.revision;
            f.apply("COPY_QUEST", "q_copy", "", "q");
            String copiedRevision = f.revision;
            var copied = f.snapshot();
            f.apply("UNDO", "", "", ""); check(helper, f.revision.equals(beforeCopy), "one undo restores normalized source");
            f.apply("REDO", "", "", ""); check(helper, f.revision.equals(copiedRevision), "redo restores exact IDs and config");
            var stale = AuthorApi.editor().copyTask(admin, f.token, f.book, beforeCopy, f.id("q"), f.id("item"),
                    new TaskDefinition(f.book, f.id("stale"), ResourceLocation.parse("brnquest:item"), legacy, false));
            check(helper, stale.code().equals("STALE_DRAFT_REVISION"), "stale copy rejected before normalization");
            var invalidWire = new AuthoringNetwork.EditorMutationWire(f.token.toString(), f.book.toString(), f.revision,
                    "UPDATE_TASK", f.raw("item"), f.raw("q"), f.raw("item"), "", 0, 0, 0, List.of(),
                    Map.of("item", "{id:\"missing:unknown\",count:1}"));
            f.handler.mutate(AuthoringRequestDecoder.mutation(GSON.toJson(invalidWire)).value());
            check(helper, last(f.packets).code().equals("INVALID_EDITOR_MUTATION"), "invalid registry values return editor diagnostics");
            check(helper, f.snapshot().equals(copied), "rejected normalization changes no draft state");
            f.apply("COPY_CHAPTER", "c", "", "");
            var saved = EditSessionService.get().snapshot(admin, f.token, f.book, f.revision).value();
            var diskBefore = new DraftRepository().load(admin.getServer(), f.book).value();
            check(helper, new DraftRepository().save(admin.getServer(), saved, diskBefore.draftRevision()).success(), "normalized draft saves");
            var loaded = new DraftRepository().load(admin.getServer(), f.book).value();
            check(helper, loaded.draftRevision().equals(f.revision), "native disk round trip preserves normalized revision");
            check(helper, loaded.book().quests().stream().allMatch(q -> q.tasks().get(1).config().get("private_ref").equals("brnquest:q")
                    && q.rewards().getFirst().config().get("opaque").equals("keep")), "all copies preserve opaque config without remapping private strings");
            helper.succeed();
        } finally { release(admin); }
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
            fixture.apply("PASTE_TASK", "task_pasted", "q2", "", Map.of("snapshot",
                    yourscraft.jasdewstarfield.brnquest.author.TypedEntrySnapshot.of(fixture.quest("q").tasks().getFirst()).encode()));
            fixture.apply("PASTE_REWARD", "reward_pasted", "q2", "", Map.of("snapshot",
                    yourscraft.jasdewstarfield.brnquest.author.TypedEntrySnapshot.of(fixture.quest("q").rewards().getFirst()).encode()));
            fixture.checkQuestProperties();
            fixture.apply("COPY_QUEST", "q_copy", "", "q");
            check(helper, fixture.quest("q_copy").tasks().getFirst().config().equals(opaque), "quest copy uses server-owned nested task data");
            // Exercise translation copying through the actual decoder, handler and draft transaction.
            check(helper, BookText.quest(fixture.snapshot(),
                    fixture.quest("q_copy"), "zh_cn", "title", "").equals("本地化标题（副本）"),
                    "quest copy preserves server-owned localized text");
            check(helper, BookText.resolveQuestDescription(fixture.snapshot(), fixture.quest("q_copy"), "zh_cn")
                    .format().serializedName().equals("markdown_v1"),
                    "quest copy preserves the localized description format with its text");
            String beforeUndo = fixture.revision;
            fixture.apply("UNDO", "", "", "");
            check(helper, fixture.snapshot().quests().stream().noneMatch(q -> q.id().equals(fixture.id("q_copy"))), "undo removes copied quest");
            fixture.apply("REDO", "", "", "");
            check(helper, fixture.revision.equals(beforeUndo), "redo restores the exact semantic revision");
            var frozenQuest = yourscraft.jasdewstarfield.brnquest.author.QuestClipboardSnapshot.capture(fixture.snapshot(), java.util.Set.of(fixture.id("q"))).encode();
            fixture.apply("PASTE_QUESTS", "c2", "", "", Map.of("snapshot", frozenQuest));
            fixture.apply("UNDO", "", "", "");
            fixture.apply("COPY_CHAPTER", "c", "", "");
            fixture.apply("UNDO", "", "", "");
            var nodeSelection = List.of(new AuthoringNetwork.PositionWire(
                    yourscraft.jasdewstarfield.brnquest.author.CanvasSelectionKey.quest(fixture.id("q")).toString(), 7, 8));
            for (var action : List.of("MOVE_CANVAS_SELECTION", "COPY_CANVAS_SELECTION", "DELETE_CANVAS_SELECTION")) {
                fixture.canvasSelection(action, fixture.quest("q").chapterId().getPath(), nodeSelection);
                fixture.apply("UNDO", "", "", "");
            }
            fixture.apply("COPY_QUESTS", "", "", "");
            fixture.apply("UNDO", "", "", "");
            fixture.apply("DELETE_QUESTS", "", "", "");
            fixture.apply("UNDO", "", "", "");
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
            fixture.apply("UPDATE_BOOK_PROPERTIES", "book", "", "");
            fixture.apply("UPDATE_CANVAS", fixture.book.getPath(), "", "", Map.of("scene", CanvasScene.EMPTY.encode()));
            check(helper, fixture.seen.size() == AuthoringMutationAction.values().length, "all mutation actions plus review executed");
            helper.succeed();
        } finally { release(admin); }
    }

    @GameTest(template = "empty", timeoutTicks = 600, batch = "authoringHandlerMutations")
    @PrefixGameTestTemplate(false)
    public static void groupMetadataSurvivesLegacyUpdatesAndUndo(GameTestHelper helper) {
        var admin = helper.makeMockServerPlayerInLevel(); op(admin);
        try {
            var fixture = new MutationFixture(helper, admin);
            fixture.apply("ADD_GROUP", "g", "", "");
            fixture.apply("UPDATE_GROUP", "g", "", "", Map.of("icon", "texture:minecraft:textures/item/book.png", "description", "Group info"));
            var configured = fixture.snapshot().chapterGroups().getFirst();
            fixture.apply("UPDATE_GROUP", "g", "", "");
            check(helper, fixture.snapshot().chapterGroups().getFirst().equals(configured), "old clients preserve omitted metadata");
            fixture.apply("UPDATE_GROUP", "g", "", "", Map.of("icon", "", "description", ""));
            check(helper, fixture.snapshot().chapterGroups().getFirst().icon().isEmpty(), "explicit empty icon clears it");
            fixture.apply("UNDO", "", "", "");
            check(helper, fixture.snapshot().chapterGroups().getFirst().equals(configured), "one undo restores group metadata");
            fixture.apply("REDO", "", "", "");
            check(helper, fixture.snapshot().chapterGroups().getFirst().description().isEmpty(), "redo restores explicit clearing");
            helper.succeed();
        } finally { release(admin); }
    }


    /** One packet stages several languages, and one undo restores both metadata and every translation. */
    @GameTest(template = "empty", timeoutTicks = 600, batch = "authoringHandlerMutations")
    @PrefixGameTestTemplate(false)
    public static void localizedSingleLineBatchIsAtomic(GameTestHelper helper) {
        var admin = helper.makeMockServerPlayerInLevel(); op(admin);
        try {
            var fixture = new MutationFixture(helper, admin);
            fixture.apply("ADD_GROUP", "g", "", "");
            fixture.apply("ADD_CHAPTER", "c", "g", "");
            fixture.apply("ADD_QUEST", "q", "c", "");
            var before = fixture.snapshot();
            fixture.apply("UPDATE_GROUP", "g", "", "", Map.of("text_field", "title", "text_locale.zh_cn", "Chinese group",
                    "text_locale.en_us", "English group", "description", "Info"));
            check(helper, fixture.snapshot().chapterGroups().getFirst().title().equals("English group"), "fallback title updates native field");
            check(helper, fixture.snapshot().localization().resolve("zh_cn", "chapter_group.brnquest:g.title", "").equals("Chinese group"), "Chinese group retained");
            fixture.apply("UNDO", "", "", "");
            check(helper, fixture.snapshot().equals(before), "single undo restores all group languages and metadata");
            fixture.apply("UPDATE_CHAPTER", "c", "g", "", Map.of("text_field", "title", "text_locale.fr_fr", "French chapter"));
            check(helper, fixture.snapshot().chapters().getFirst().title().equals(before.chapters().getFirst().title()), "translated chapter does not overwrite native title");
            var beforeQuest = fixture.snapshot();
            fixture.apply("UPDATE_QUEST_TRANSLATION", "q", "", "", Map.of("text_field", "title", "text_locale.zh_cn", "Chinese quest", "text_locale.fr_fr", "French quest"));
            var translations = fixture.snapshot().localization().translations().get("zh_cn");
            check(helper, translations.size() == 1 && translations.containsKey("quest.brnquest:q.title"), "quick edit only writes its own field");
            fixture.apply("UNDO", "", "", "");
            check(helper, fixture.snapshot().equals(beforeQuest), "one undo reverts both quest translations");
            helper.succeed();
        } finally { release(admin); }
    }


    /** Real author packets freeze defaults at creation and retain atomic undo for template changes. */
    @GameTest(template = "empty", timeoutTicks = 600, batch = "authoringHandlerMutations")
    @PrefixGameTestTemplate(false)
    public static void creationTemplatesAreAppliedOnlyAtCreation(GameTestHelper helper) {
        var admin = helper.makeMockServerPlayerInLevel(); op(admin);
        try {
            var fixture = new MutationFixture(helper, admin);
            fixture.apply("ADD_GROUP", "g", "", "");
            fixture.apply("ADD_CHAPTER", "c", "g", "");
            fixture.apply("ADD_QUEST", "old", "c", "");
            var original = fixture.quest("old");
            fixture.apply("UPDATE_BOOK_PROPERTIES", "book", "", "", Map.of("quest_defaults", "{\"size\":2,\"repeatable\":true,\"min_width\":4,\"invisible_until_complete\":true,\"visible_after_tasks\":2,\"hide_lock_icon\":true,\"dependency_requirement\":\"one_started\",\"repeat_cooldown_seconds\":120,\"ignore_reward_blocking\":true}"));
            check(helper, fixture.quest("old").equals(original), "existing quests remain unchanged");
            var bookConfigured = fixture.snapshot();
            fixture.apply("UPDATE_CHAPTER", "c", "g", "", Map.of("quest_defaults", "{\"repeatable\":false,\"min_width\":0}"));
            fixture.apply("ADD_QUEST", "q", "c", "", Map.of("quest_defaults", "{\"icon_scale\":1.5}"));
            var created = fixture.quest("q");
            check(helper, created.appearance().size() == 2 && created.appearance().iconScale() == 1.5
                    && created.appearance().minWidth() == 0 && !created.behavior().repeatable(), "book, chapter and explicit overrides resolve once");
            check(helper, created.behavior().invisibleUntilComplete() && created.behavior().visibleAfterTasks() == 2
                    && created.behavior().hideLockIcon() && created.behavior().repeatCooldownSeconds() == 120
                    && created.behavior().ignoreRewardBlocking()
                    && created.behavior().dependencyRequirement() == yourscraft.jasdewstarfield.brnquest.data.DependencyRequirement.ONE_STARTED,
                    "extended behavior defaults survive the real packet and creation path");
            fixture.apply("UNDO", "", "", "");
            fixture.apply("UNDO", "", "", "");
            check(helper, fixture.snapshot().equals(bookConfigured), "chapter template undo restores the complete prior book");
            fixture.apply("REDO", "", "", "");
            fixture.apply("REDO", "", "", "");
            check(helper, fixture.quest("q").equals(created), "redo restores explicit created values");
            fixture.apply("COPY_QUEST", "copy", "", "q");
            check(helper, fixture.quest("copy").appearance().equals(created.appearance()), "copy preserves source values");
            helper.succeed();
        } finally { release(admin); }
    }

    /** Real packets cover new-entry defaults, chapter false overrides, copies and settings undo. */
    @GameTest(template = "empty", timeoutTicks = 600, batch = "authoringHandlerMutations")
    @PrefixGameTestTemplate(false)
    public static void entryDefaultsAreMaterializedThroughAuthorPackets(GameTestHelper helper) {
        var admin = helper.makeMockServerPlayerInLevel(); op(admin);
        try {
            var f = new MutationFixture(helper, admin);
            f.apply("ADD_GROUP", "g", "", ""); f.apply("ADD_CHAPTER", "c", "g", ""); f.apply("ADD_QUEST", "q", "c", "");
            f.apply("UPDATE_BOOK_PROPERTIES", "book", "", "", Map.of("book_settings",
                    "{\"consume_items\":true,\"reward_team\":true,\"reward_claim_policy\":\"auto_hidden\",\"suppress_auto_claim\":true,\"pause_game\":true}"));
            var configured = f.snapshot();
            f.apply("ADD_TASK", "t", "q", "item", Map.of("item", "{id:'minecraft:stone',count:1}"));
            check(helper, f.quest("q").tasks().getFirst().config().get("consume_items").equals("true"), "book consumption reaches type normalization");
            f.apply("UPDATE_CHAPTER", "c", "g", "", Map.of("default_consume_items", "false"));
            f.apply("ADD_TASK", "t2", "q", "item", Map.of("item", "{id:'minecraft:stone',count:1}"));
            check(helper, f.quest("q").tasks().getLast().config().get("consume_items").equals("false"), "chapter false overrides book true");
            f.apply("COPY_TASK", "tcopy", "q", "t");
            check(helper, f.quest("q").tasks().getLast().config().get("consume_items").equals("true"), "copy retains source consumption");
            f.apply("ADD_REWARD", "r", "q", "xp", Map.of("xp", "3"));
            var reward = f.quest("q").rewards().getFirst();
            check(helper, reward.teamReward() && reward.claimPolicy().equals("auto_hidden"), "new rewards receive shared defaults");
            check(helper, f.snapshot().settings().equals(configured.settings()), "all immutable rebuilds retain book settings");
            f.apply("UPDATE_BOOK_PROPERTIES", "book", "", "", Map.of("book_settings", "{}"));
            check(helper, f.quest("q").rewards().getFirst().equals(reward), "changing defaults does not rewrite existing rewards");
            f.apply("UNDO", "", "", "");
            check(helper, f.snapshot().settings().equals(configured.settings()), "undo restores complete book policy");
            helper.succeed();
        } finally { release(admin); }
    }

    /** Focus travels through the real mutation decoder, snapshot response and history restoration. */
    @GameTest(template = "empty", timeoutTicks = 600, batch = "authoringHandlerMutations")
    @PrefixGameTestTemplate(false)
    public static void chapterAutofocusSurvivesPacketsAndUndo(GameTestHelper helper) {
        var admin = helper.makeMockServerPlayerInLevel(); op(admin);
        try {
            var f = new MutationFixture(helper, admin);
            f.apply("ADD_GROUP", "g", "", ""); f.apply("ADD_CHAPTER", "c", "g", ""); f.apply("ADD_QUEST", "q", "c", "");
            f.apply("UPDATE_CHAPTER", "c", "g", "", Map.of("autofocus_id", f.id("q").toString()));
            check(helper, f.id("q").equals(f.snapshot().chapters().getFirst().autofocusQuestId()), "packet sets focus");
            f.apply("UPDATE_CHAPTER", "c", "g", "", Map.of("icon", ""));
            check(helper, f.id("q").equals(f.snapshot().chapters().getFirst().autofocusQuestId()), "legacy metadata packet retains focus");
            f.apply("UPDATE_CHAPTER", "c", "g", "", Map.of("autofocus_id", ""));
            check(helper, f.snapshot().chapters().getFirst().autofocusQuestId() == null, "explicit empty clears focus");
            f.apply("UNDO", "", "", "");
            check(helper, f.id("q").equals(f.snapshot().chapters().getFirst().autofocusQuestId()), "undo restores reference");
            helper.succeed();
        } finally { release(admin); }
    }

    /** Copy is read-only; pasting a deleted source still validates and forms one history step per new ID. */
    @GameTest(template = "empty", timeoutTicks = 600, batch = "authoringHandlerMutations")
    @PrefixGameTestTemplate(false)
    public static void typedClipboardRetainsDeletedSourceAndAtomicHistory(GameTestHelper helper) {
        var admin = helper.makeMockServerPlayerInLevel(); op(admin);
        try {
            var f = new MutationFixture(helper, admin);
            f.apply("ADD_GROUP", "g", "", ""); f.apply("ADD_CHAPTER", "c", "g", "");
            f.apply("ADD_QUEST", "q", "c", ""); f.apply("ADD_QUEST", "destination", "c", "");
            var opaque = Map.of("title.zh_cn", "复制目标", "title.en_us", "Copied task", "private_ref", "brnquest:original");
            f.apply("ADD_TASK", "source", "q", "opaque_task", opaque);
            f.apply("UPDATE_TASK", "source", "q", "source", opaque);
            var copy = yourscraft.jasdewstarfield.brnquest.author.TypedEntrySnapshot.of(f.quest("q").tasks().getFirst());
            f.apply("DELETE_TASK", "source", "q", "");
            var before = f.snapshot();
            f.apply("PASTE_TASK", "copy1", "destination", "", Map.of("snapshot", copy.encode()));
            check(helper, f.quest("destination").tasks().getFirst().config().equals(opaque)
                    && f.quest("destination").tasks().getFirst().optional(), "paste retains deleted source configuration and optional flag");
            f.apply("UNDO", "", "", "");
            check(helper, f.snapshot().equals(before), "one undo restores the whole book");
            f.apply("REDO", "", "", "");
            f.apply("PASTE_TASK", "copy2", "destination", "", Map.of("snapshot", copy.encode()));
            check(helper, f.quest("destination").tasks().size() == 2, "repeated paste has independent IDs");
            String revision = f.revision;
            var wire = new AuthoringNetwork.EditorMutationWire(f.token.toString(), f.book.toString(), revision,
                    "PASTE_TASK", f.raw("copy2"), f.raw("destination"), "", "", 0, 0, 0, List.of(), Map.of("snapshot", copy.encode()));
            f.handler.mutate(AuthoringRequestDecoder.mutation(GSON.toJson(wire)).value());
            check(helper, last(f.packets).code().equals("DUPLICATE_TYPED_ID"), "duplicate destination IDs are rejected");
            check(helper, EditSessionService.get().inspect(admin, f.book).value().draftRevision().equals(revision), "failed paste has no partial change");
            helper.succeed();
        } finally { release(admin); }
    }

    /** Real packets ensure multi-node graph edits are a single history step, not independent partial edits. */
    @GameTest(template = "empty", timeoutTicks = 600, batch = "authoringHandlerMutations")
    @PrefixGameTestTemplate(false)
    public static void questSelectionCopiesAndDeletesAtomically(GameTestHelper helper) {
        var admin = helper.makeMockServerPlayerInLevel(); op(admin);
        try {
            var f = new MutationFixture(helper, admin);
            f.apply("ADD_GROUP", "g", "", ""); f.apply("ADD_CHAPTER", "c", "g", "");
            f.apply("ADD_QUEST", "q", "c", ""); f.apply("ADD_QUEST", "q2", "c", "");
            f.apply("ADD_DEPENDENCY", "q2", "", "q");
            var original = f.snapshot();
            f.selection("COPY_QUESTS", List.of("q", "q2"));
            var copied = f.snapshot();
            check(helper, copied.quests().size() == 4, "both nodes copied");
            var a = copied.quests().stream().filter(q -> q.id().getPath().startsWith("q_copy_")).findFirst().orElseThrow();
            var b = copied.quests().stream().filter(q -> q.id().getPath().startsWith("q2_copy_")).findFirst().orElseThrow();
            check(helper, b.dependencies().equals(List.of(a.id())), "internal edge targets copied quest");
            f.apply("UNDO", "", "", ""); check(helper, f.snapshot().equals(original), "one undo removes whole copy");
            f.apply("REDO", "", "", ""); check(helper, f.snapshot().equals(copied), "redo restores exact copied IDs");
            f.selection("DELETE_QUESTS", List.of("q", "q2"));
            check(helper, f.snapshot().quests().size() == 2, "whole original selection deleted");
            f.apply("UNDO", "", "", ""); check(helper, f.snapshot().equals(copied), "one undo restores whole deletion");
            helper.succeed();
        } finally { release(admin); }
    }

    /** Mixed moves, copies and deletions must restore both kinds with one undo, including mixed clipboard payloads. */
    @GameTest(template = "empty", timeoutTicks = 600, batch = "authoringHandlerMutations")
    @PrefixGameTestTemplate(false)
    public static void mixedCanvasSelectionIsAtomic(GameTestHelper helper) {
        var admin = helper.makeMockServerPlayerInLevel(); op(admin);
        try {
            var f = new MutationFixture(helper, admin);
            f.apply("ADD_GROUP", "g", "", ""); f.apply("ADD_CHAPTER", "c", "g", ""); f.apply("ADD_QUEST", "q", "c", "");
            var art = new CanvasScene.Decoration(f.id("art"), "brnquest_local:textures/imported/local.png", -2, -1, 2, 1, true, 0, false);
            f.apply("UPDATE_CANVAS", "c", "", "", Map.of("scene", new CanvasScene(List.of(art), null, null).encode()));
            var original = f.snapshot();
            var positions = List.of(new AuthoringNetwork.PositionWire(
                    yourscraft.jasdewstarfield.brnquest.author.CanvasSelectionKey.quest(f.id("q")).toString(), 5, 6),
                    new AuthoringNetwork.PositionWire(yourscraft.jasdewstarfield.brnquest.author.CanvasSelectionKey.decoration(f.id("art")).toString(), 2, 3));
            for (var action : List.of("MOVE_CANVAS_SELECTION", "COPY_CANVAS_SELECTION", "DELETE_CANVAS_SELECTION")) {
                f.canvasSelection(action, "c", positions); var changed = f.snapshot();
                var chapter = changed.chapters().getFirst();
                if (action.equals("MOVE_CANVAS_SELECTION")) {
                    check(helper, f.quest("q").x() == 5 && chapter.canvasScene().decorations().getFirst().x() == 2, "both objects moved");
                } else if (action.equals("COPY_CANVAS_SELECTION")) {
                    check(helper, changed.quests().size() == 2 && chapter.canvasScene().decorations().size() == 2, "both objects copied");
                } else check(helper, changed.quests().isEmpty() && chapter.canvasScene().decorations().isEmpty(), "both objects deleted");
                f.apply("UNDO", "", "", ""); check(helper, f.snapshot().equals(original), "one undo restores both kinds");
                f.apply("REDO", "", "", ""); check(helper, f.snapshot().equals(changed), "redo preserves exact mixed result and IDs");
                f.apply("UNDO", "", "", "");
            }
            var frozen = yourscraft.jasdewstarfield.brnquest.author.QuestClipboardSnapshot.capture(original, f.id("c"), java.util.Set.of(f.id("q")), java.util.Set.of(f.id("art")));
            f.apply("ADD_CHAPTER", "dest", "g", ""); var beforePaste = f.snapshot();
            f.apply("PASTE_QUESTS", "dest", "", "", Map.of("snapshot", frozen.encode()));
            var dest = f.snapshot().chapters().stream().filter(c -> c.id().equals(f.id("dest"))).findFirst().orElseThrow();
            check(helper, dest.quests().size() == 1 && dest.canvasScene().decorations().size() == 1, "remote paste contains both kinds without local texture resources");
            f.apply("UNDO", "", "", ""); check(helper, f.snapshot().equals(beforePaste), "mixed paste is one undo");
            helper.succeed();
        } finally { release(admin); }
    }

    /** A complete chapter must publish and restore as one authoritative history entry. */
    @GameTest(template = "empty", timeoutTicks = 600, batch = "authoringHandlerMutations")
    @PrefixGameTestTemplate(false)
    public static void chapterCopyIsAtomic(GameTestHelper helper) {
        var admin = helper.makeMockServerPlayerInLevel(); op(admin);
        try {
            var f = new MutationFixture(helper, admin);
            f.apply("ADD_GROUP", "g", "", ""); f.apply("ADD_CHAPTER", "c", "g", "");
            f.apply("ADD_QUEST", "q", "c", ""); f.apply("ADD_QUEST", "q2", "c", "");
            f.apply("ADD_DEPENDENCY", "q2", "", "q");
            var before = f.snapshot();
            f.apply("COPY_CHAPTER", "c", "", "");
            var after = f.snapshot();
            var copy = after.chapters().stream().filter(c -> !c.id().equals(f.id("c"))).findFirst().orElseThrow();
            check(helper, copy.quests().size() == 2, "whole chapter copied");
            check(helper, copy.quests().get(1).dependencies().equals(List.of(copy.quests().getFirst().id())), "copied edge remains internal");
            f.apply("UNDO", "", "", ""); check(helper, f.snapshot().equals(before), "one undo removes chapter and contents");
            f.apply("REDO", "", "", ""); check(helper, f.snapshot().equals(after), "redo restores exact chapter identities");
            helper.succeed();
        } finally { release(admin); }
    }

    /** Deleted sources and navigation do not invalidate frozen definitions or their one-step history. */
    @GameTest(template = "empty", timeoutTicks = 600, batch = "authoringHandlerMutations")
    @PrefixGameTestTemplate(false)
    public static void crossChapterSnapshotSurvivesSourceDeletion(GameTestHelper helper) {
        var admin = helper.makeMockServerPlayerInLevel(); op(admin);
        try {
            var f = new MutationFixture(helper, admin);
            f.apply("ADD_GROUP", "g", "", ""); f.apply("ADD_CHAPTER", "c", "g", ""); f.apply("ADD_CHAPTER", "dest", "g", "");
            f.apply("ADD_QUEST", "q", "c", ""); f.apply("ADD_QUEST", "q2", "c", ""); f.apply("ADD_DEPENDENCY", "q2", "", "q");
            String snapshot = yourscraft.jasdewstarfield.brnquest.author.QuestClipboardSnapshot.capture(f.snapshot(), java.util.Set.of(f.id("q"), f.id("q2"))).encode();
            f.apply("DELETE_CHAPTER", "c", "", "");
            var before = f.snapshot();
            f.apply("PASTE_QUESTS", "dest", "", "", Map.of("snapshot", snapshot));
            var after = f.snapshot();
            check(helper, after.quests().size() == 2 && after.quests().stream().allMatch(q -> q.chapterId().equals(f.id("dest"))), "pasted into existing destination after source deletion");
            check(helper, after.quests().get(1).dependencies().equals(List.of(after.quests().getFirst().id())), "frozen internal dependency remapped");
            f.apply("UNDO", "", "", ""); check(helper, f.snapshot().equals(before), "whole paste undone");
            f.apply("REDO", "", "", ""); check(helper, f.snapshot().equals(after), "exact paste restored");
            f.apply("PASTE_QUESTS", "dest", "", "", Map.of("snapshot", snapshot));
            check(helper, f.snapshot().quests().size() == 4, "repeated paste allocates fresh identities");
            helper.succeed();
        } finally { release(admin); }
    }

    /** Chapter defaults survive older metadata packets, quest edits and atomic history. */
    @GameTest(template = "empty", timeoutTicks = 600, batch = "authoringHandlerMutations")
    @PrefixGameTestTemplate(false)
    public static void dependencyLineSettingsPersistAcrossAuthoring(GameTestHelper helper) {
        var admin=helper.makeMockServerPlayerInLevel(); op(admin);
        try {
            var f=new MutationFixture(helper,admin);
            f.apply("ADD_GROUP","g","",""); f.apply("ADD_CHAPTER","c","g",""); f.apply("ADD_QUEST","q","c","");
            f.apply("UPDATE_CHAPTER","c","g","",Map.of("default_hide_dependency_lines","true"));
            check(helper,f.snapshot().chapters().getFirst().defaultHideDependencyLines(),"chapter default enabled");
            f.apply("UPDATE_CHAPTER","c","g","");
            check(helper,f.snapshot().chapters().getFirst().defaultHideDependencyLines(),"older packet preserves new default");
            for (String value : List.of("false","true","default")) {
                var request = new AuthoringRequestDecoder.QuestRequest(f.token,f.book,f.revision,f.id("q"),f.id("q"),"Q","","",
                        null,null,true,null,null,null,null,value);
                new AuthoringQuestUpdateHandler(admin,f.sender).update(request);
                var reply=last(f.packets); f.revision=reply.draftRevision();
                check(helper,java.util.Objects.equals(f.quest("q").appearance().hideDependencyLines(),value.equals("default")?null:Boolean.valueOf(value)),"task tri-state persisted");
            }
            f.apply("UNDO","","","");
            check(helper,Boolean.TRUE.equals(f.quest("q").appearance().hideDependencyLines()),"undo restores explicit hide");
            f.apply("COPY_CHAPTER","c","","");
            check(helper,f.snapshot().chapters().stream().allMatch(c->c.defaultHideDependencyLines()),"chapter copies preserve default");
            helper.succeed();
        } finally { release(admin); }
    }

    /** Artwork shares the real session boundary: permissions, stale revisions, one undo step and save/reload. */
    @GameTest(template = "empty", timeoutTicks = 600, batch = "authoringHandlerMutations")
    @PrefixGameTestTemplate(false)
    public static void canvasArtworkRemainsAtomicAndServerOwned(GameTestHelper helper) {
        var admin = helper.makeMockServerPlayerInLevel(); op(admin);
        var ordinary = helper.makeMockServerPlayerInLevel();
        try {
            var f = new MutationFixture(helper, admin);
            f.apply("ADD_GROUP", "g", "", ""); f.apply("ADD_CHAPTER", "c", "g", ""); f.apply("ADD_QUEST", "q", "c", "");
            var before = f.snapshot(); String beforeRevision = f.revision;
            var art = new CanvasScene.Decoration(f.id("art"), "missing:textures/art.png", 3, -2, 4, 2, true, 1, false);
            var scene = new CanvasScene(List.of(art), new CanvasScene.Background("missing:textures/bg.png", CanvasScene.Fit.COVER, 0.5, 2), null);
            f.apply("UPDATE_CANVAS", "c", "", "", Map.of("scene", scene.encode()));
            check(helper, f.snapshot().chapters().getFirst().canvasScene().equals(scene), "server retains unloaded resource IDs");
            check(helper, f.snapshot().quests().equals(before.quests()), "artwork never changes task configuration");
            String editedRevision = f.revision;
            f.apply("UNDO", "", "", "");
            check(helper, f.snapshot().equals(before), "single undo restores exact original book");
            f.apply("REDO", "", "", "");
            check(helper, f.revision.equals(editedRevision), "redo restores identical content revision");
            // Property-form backgrounds share the metadata transaction and preserve existing artwork.
            var decorated = f.snapshot();
            var backgrounds = new CanvasScene(List.of(), null, scene.canvas(), true);
            f.apply("UPDATE_CHAPTER", "c", "g", "", Map.of("backgrounds", backgrounds.encode(), "icon", "minecraft:stone"));
            var updatedChapter = f.snapshot().chapters().getFirst();
            check(helper, updatedChapter.canvasScene().decorations().equals(List.of(art))
                    && updatedChapter.canvasScene().backgrounds().equals(backgrounds)
                    && updatedChapter.icon().equals("minecraft:stone"), "chapter metadata and backgrounds update together");
            f.apply("UNDO", "", "", "");
            check(helper, f.snapshot().equals(decorated), "one undo restores chapter metadata and backgrounds");
            f.apply("UPDATE_BOOK_PROPERTIES", "book", "", "", Map.of("backgrounds", backgrounds.encode(), "book_settings", "{\"pause_game\":true}"));
            check(helper, f.snapshot().canvasScene().equals(backgrounds) && f.snapshot().settings().pauseGame(), "book settings and backgrounds update together");
            f.apply("UNDO", "", "", "");
            check(helper, f.snapshot().equals(decorated), "one undo restores book metadata and backgrounds");
            f.apply("UPDATE_CHAPTER", "c", "g", "", Map.of("icon", "minecraft:stone"));
            check(helper, f.snapshot().chapters().getFirst().canvasScene().equals(scene), "legacy metadata request preserves all artwork");
            f.apply("UNDO", "", "", "");
            // The existing session policy clears history on stale revision conflicts; test rejection after history.
            var stale = new AuthoringNetwork.EditorMutationWire(f.token.toString(), f.book.toString(), beforeRevision,
                    "UPDATE_CANVAS", f.raw("c"), "", "", "", 0, 0, 0, List.of(), Map.of("scene", CanvasScene.EMPTY.encode()));
            f.handler.mutate(AuthoringRequestDecoder.mutation(GSON.toJson(stale)).value());
            check(helper, last(f.packets).code().equals("STALE_DRAFT_REVISION"), "stale artwork replacement rejected");
            var denied = new DraftEditService().updateCanvas(ordinary, f.token, f.book, editedRevision, f.id("c"), CanvasScene.EMPTY);
            check(helper, !denied.success(), "ordinary player cannot edit shared decoration");
            check(helper, f.snapshot().chapters().getFirst().canvasScene().equals(scene), "rejected changes leave no partial scene");
            new AuthoringPublicationHandler(admin, f.sender).save(new AuthoringRequestDecoder.SessionRequest(f.token, f.book, f.revision));
            check(helper, last(f.packets).savedRevision().equals(f.revision), "artwork saved through publication boundary");
            var reload = NativeBookJson.decode(com.google.gson.JsonParser.parseString(NativeBookJson.encode(f.snapshot())).getAsJsonObject());
            check(helper, reload.chapters().getFirst().canvasScene().equals(scene), "native load retains geometry and background");
            f.apply("COPY_CHAPTER", "c", "", "");
            var scenes = f.snapshot().chapters().stream().map(ChapterDefinition::canvasScene).toList();
            check(helper, scenes.size() == 2 && !scenes.get(0).decorations().getFirst().id().equals(scenes.get(1).decorations().getFirst().id()), "chapter copying remaps artwork IDs");
            helper.succeed();
        } finally { release(admin); release(ordinary); }
    }

}
