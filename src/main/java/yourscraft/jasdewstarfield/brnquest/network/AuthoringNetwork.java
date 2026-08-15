package yourscraft.jasdewstarfield.brnquest.network;

import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.brnquest.BRNQuest;
import yourscraft.jasdewstarfield.brnquest.BrnQuestConstants;
import yourscraft.jasdewstarfield.brnquest.api.AuthorApi;
import yourscraft.jasdewstarfield.brnquest.author.AuthorOperationResult;
import yourscraft.jasdewstarfield.brnquest.author.DraftCatalogEntry;
import yourscraft.jasdewstarfield.brnquest.author.DraftBookEditor;
import yourscraft.jasdewstarfield.brnquest.author.DraftEditResult;
import yourscraft.jasdewstarfield.brnquest.author.DraftSnapshot;
import yourscraft.jasdewstarfield.brnquest.author.EditSessionHandle;
import yourscraft.jasdewstarfield.brnquest.author.EditSessionService;
import yourscraft.jasdewstarfield.brnquest.author.EditSessionView;
import yourscraft.jasdewstarfield.brnquest.client.ClientPayloadHandler;
import yourscraft.jasdewstarfield.brnquest.data.NativeBookJson;
import yourscraft.jasdewstarfield.brnquest.data.ChapterDefinition;
import yourscraft.jasdewstarfield.brnquest.data.ChapterGroupDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestDefinition;
import yourscraft.jasdewstarfield.brnquest.data.RewardDefinition;
import yourscraft.jasdewstarfield.brnquest.data.TaskDefinition;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Stage-5 authoring channel. Every request is re-authorized against the connected target server. */
public final class AuthoringNetwork {
    private static final Gson GSON = new Gson();

    private AuthoringNetwork() {}

    public record RequestCatalogPayload() implements CustomPacketPayload {
        public static final Type<RequestCatalogPayload> TYPE = AuthoringNetwork.type("editor_catalog_request");
        public static final StreamCodec<ByteBuf, RequestCatalogPayload> CODEC = StreamCodec.unit(new RequestCatalogPayload());
        public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record OpenSessionPayload(String bookId) implements CustomPacketPayload {
        public static final Type<OpenSessionPayload> TYPE = AuthoringNetwork.type("editor_session_open");
        public static final StreamCodec<ByteBuf, OpenSessionPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, OpenSessionPayload::bookId, OpenSessionPayload::new);
        public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record OpenCurrentSessionPayload(String bookId) implements CustomPacketPayload {
        public static final Type<OpenCurrentSessionPayload> TYPE = AuthoringNetwork.type("editor_session_open_current");
        public static final StreamCodec<ByteBuf, OpenCurrentSessionPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, OpenCurrentSessionPayload::bookId, OpenCurrentSessionPayload::new);
        public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record RenewSessionPayload(String sessionId, String draftRevision) implements CustomPacketPayload {
        public static final Type<RenewSessionPayload> TYPE = AuthoringNetwork.type("editor_session_renew");
        public static final StreamCodec<ByteBuf, RenewSessionPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, RenewSessionPayload::sessionId,
                ByteBufCodecs.STRING_UTF8, RenewSessionPayload::draftRevision,
                RenewSessionPayload::new);
        public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record CloseSessionPayload(String sessionId, String draftRevision) implements CustomPacketPayload {
        public static final Type<CloseSessionPayload> TYPE = AuthoringNetwork.type("editor_session_close");
        public static final StreamCodec<ByteBuf, CloseSessionPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, CloseSessionPayload::sessionId,
                ByteBufCodecs.STRING_UTF8, CloseSessionPayload::draftRevision,
                CloseSessionPayload::new);
        public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SaveSessionPayload(String sessionId, String bookId, String draftRevision)
            implements CustomPacketPayload {
        public static final Type<SaveSessionPayload> TYPE = AuthoringNetwork.type("editor_session_save");
        public static final StreamCodec<ByteBuf, SaveSessionPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, SaveSessionPayload::sessionId,
                ByteBufCodecs.STRING_UTF8, SaveSessionPayload::bookId,
                ByteBufCodecs.STRING_UTF8, SaveSessionPayload::draftRevision,
                SaveSessionPayload::new);
        public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record UpdateQuestPayload(String json) implements CustomPacketPayload {
        public static final Type<UpdateQuestPayload> TYPE = AuthoringNetwork.type("editor_quest_update");
        public static final StreamCodec<ByteBuf, UpdateQuestPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(BrnQuestConstants.MAX_EDITOR_METADATA_BYTES), UpdateQuestPayload::json,
                UpdateQuestPayload::new);
        public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record EditorMutationPayload(String json) implements CustomPacketPayload {
        public static final Type<EditorMutationPayload> TYPE = AuthoringNetwork.type("editor_mutation");
        public static final StreamCodec<ByteBuf, EditorMutationPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(BrnQuestConstants.MAX_EDITOR_METADATA_BYTES), EditorMutationPayload::json,
                EditorMutationPayload::new);
        public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record CatalogPayload(String json) implements CustomPacketPayload {
        public static final Type<CatalogPayload> TYPE = AuthoringNetwork.type("editor_catalog");
        public static final StreamCodec<ByteBuf, CatalogPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(BrnQuestConstants.MAX_EDITOR_METADATA_BYTES), CatalogPayload::json,
                CatalogPayload::new);
        public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SessionPayload(String json) implements CustomPacketPayload {
        public static final Type<SessionPayload> TYPE = AuthoringNetwork.type("editor_session");
        public static final StreamCodec<ByteBuf, SessionPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(BrnQuestConstants.MAX_EDITOR_METADATA_BYTES), SessionPayload::json,
                SessionPayload::new);
        public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record DraftChunkPayload(String sessionId, String revision, int index, String data)
            implements CustomPacketPayload {
        public static final Type<DraftChunkPayload> TYPE = AuthoringNetwork.type("editor_draft_chunk");
        public static final StreamCodec<ByteBuf, DraftChunkPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, DraftChunkPayload::sessionId,
                ByteBufCodecs.STRING_UTF8, DraftChunkPayload::revision,
                ByteBufCodecs.VAR_INT, DraftChunkPayload::index,
                ByteBufCodecs.stringUtf8(BrnQuestNetwork.BOOK_CHUNK_CHARACTERS), DraftChunkPayload::data,
                DraftChunkPayload::new);
        public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record CatalogEntryWire(String bookId, String title, String draftRevision, String origin) {}

    public record CatalogResponseWire(String status, String code, String message, boolean allowed,
                                      List<CatalogEntryWire> entries) {}

    public record SessionResponseWire(String action, String status, String code, String message,
                                      String sessionId, String bookId, String baseRevision,
                                      String draftRevision, String savedRevision, long remainingTicks,
                                      int chunks, int decodedBytes) {}

    public record QuestUpdateWire(String sessionId, String bookId, String draftRevision, String questId,
                                  String title, String subtitle, String description) {}

    public record PositionWire(String questId, double x, double y) {}

    public record EditorMutationWire(String sessionId, String bookId, String draftRevision, String action,
                                     String targetId, String parentId, String sourceId, String title,
                                     int targetIndex, double x, double y, List<PositionWire> positions) {}

    static void register(PayloadRegistrar registrar) {
        registrar.playToServer(RequestCatalogPayload.TYPE, RequestCatalogPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) sendCatalog(player);
        });
        registrar.playToServer(OpenSessionPayload.TYPE, OpenSessionPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) open(player, payload.bookId());
        });
        registrar.playToServer(OpenCurrentSessionPayload.TYPE, OpenCurrentSessionPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) openCurrent(player, payload.bookId());
        });
        registrar.playToServer(RenewSessionPayload.TYPE, RenewSessionPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) renew(player, payload.sessionId(), payload.draftRevision());
        });
        registrar.playToServer(CloseSessionPayload.TYPE, CloseSessionPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) close(player, payload.sessionId(), payload.draftRevision());
        });
        registrar.playToServer(SaveSessionPayload.TYPE, SaveSessionPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                save(player, payload.sessionId(), payload.bookId(), payload.draftRevision());
            }
        });
        registrar.playToServer(UpdateQuestPayload.TYPE, UpdateQuestPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) updateQuest(player, payload.json());
        });
        registrar.playToServer(EditorMutationPayload.TYPE, EditorMutationPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) mutate(player, payload.json());
        });
        registerClient(registrar, CatalogPayload.TYPE, CatalogPayload.CODEC, ClientDelegate::catalog);
        registerClient(registrar, SessionPayload.TYPE, SessionPayload.CODEC, ClientDelegate::session);
        registerClient(registrar, DraftChunkPayload.TYPE, DraftChunkPayload.CODEC, ClientDelegate::draftChunk);
    }

    public static void requestCatalog() {
        PacketDistributor.sendToServer(new RequestCatalogPayload());
    }

    public static void openSession(ResourceLocation bookId) {
        PacketDistributor.sendToServer(new OpenSessionPayload(bookId.toString()));
    }

    public static void openCurrentSession(ResourceLocation bookId) {
        PacketDistributor.sendToServer(new OpenCurrentSessionPayload(bookId.toString()));
    }

    public static void renewSession(UUID sessionId, String draftRevision) {
        PacketDistributor.sendToServer(new RenewSessionPayload(sessionId.toString(), draftRevision));
    }

    public static void closeSession(UUID sessionId, String draftRevision) {
        PacketDistributor.sendToServer(new CloseSessionPayload(sessionId.toString(), draftRevision));
    }

    public static void saveSession(UUID sessionId, ResourceLocation bookId, String draftRevision) {
        PacketDistributor.sendToServer(new SaveSessionPayload(sessionId.toString(), bookId.toString(), draftRevision));
    }

    public static void updateQuest(UUID sessionId, ResourceLocation bookId, String draftRevision,
                                   ResourceLocation questId, String title, String subtitle, String description) {
        QuestUpdateWire wire = new QuestUpdateWire(sessionId.toString(), bookId.toString(), draftRevision,
                questId.toString(), title, subtitle, description);
        PacketDistributor.sendToServer(new UpdateQuestPayload(GSON.toJson(wire)));
    }

    public static void mutate(EditorMutationWire wire) {
        PacketDistributor.sendToServer(new EditorMutationPayload(GSON.toJson(wire)));
    }

    private static void sendCatalog(ServerPlayer player) {
        AuthorOperationResult<List<DraftCatalogEntry>> result = AuthorApi.catalog(player);
        List<CatalogEntryWire> initialEntries = result.success() ? result.value().stream()
                .limit(BrnQuestConstants.MAX_EDITOR_CATALOG_ENTRIES)
                .map(entry -> new CatalogEntryWire(entry.bookId().toString(), boundedTitle(entry.title()),
                        entry.draftRevision(), entry.origin().name()))
                .toList() : List.of();
        List<CatalogEntryWire> entries = new java.util.ArrayList<>(initialEntries);
        String code = result.success() && result.value().size() > entries.size()
                ? "DRAFT_CATALOG_TRUNCATED" : result.code();
        String json;
        do {
            CatalogResponseWire response = new CatalogResponseWire(result.status().name(), code,
                    boundedMessage(result.message()), result.success(), List.copyOf(entries));
            json = GSON.toJson(response);
            if (json.getBytes(StandardCharsets.UTF_8).length <= BrnQuestConstants.MAX_EDITOR_METADATA_BYTES) break;
            if (entries.isEmpty()) return;
            entries.removeLast();
            code = "DRAFT_CATALOG_TRUNCATED";
        } while (true);
        BrnQuestNetwork.send(player, new CatalogPayload(json));
    }

    private static void open(ServerPlayer player, String rawBookId) {
        ResourceLocation bookId = ResourceLocation.tryParse(rawBookId);
        if (bookId == null) {
            sendFailure(player, "OPEN", AuthorOperationResult.Status.INVALID_REQUEST,
                    "INVALID_BOOK_ID", "Invalid draft book ID");
            return;
        }
        AuthorOperationResult<EditSessionHandle> opened = AuthorApi.open(player, bookId);
        if (!opened.success()) {
            sendFailure(player, "OPEN", opened.status(), opened.code(), opened.message());
            return;
        }
        EditSessionHandle handle = opened.value();
        AuthorOperationResult<DraftSnapshot> snapshot = EditSessionService.get().snapshot(player,
                handle.sessionId(), bookId, handle.session().draftRevision());
        if (!snapshot.success()) {
            AuthorApi.close(player, handle.sessionId(), handle.session().draftRevision());
            sendFailure(player, "OPEN", snapshot.status(), snapshot.code(), snapshot.message());
            return;
        }
        sendOpened(player, handle, snapshot.value());
    }

    private static void openCurrent(ServerPlayer player, String rawBookId) {
        ResourceLocation bookId = ResourceLocation.tryParse(rawBookId);
        var active = QuestBookManager.get().active().orElse(null);
        if (bookId == null || active == null || !bookId.equals(active.book().id())) {
            sendFailure(player, "OPEN", AuthorOperationResult.Status.INVALID_REQUEST,
                    "ACTIVE_BOOK_CHANGED", "The displayed task book is no longer active on this server");
            return;
        }
        AuthorOperationResult<DraftSnapshot> created = AuthorApi.createFromActive(player);
        if (!created.success() && created.status() != AuthorOperationResult.Status.CONFLICT) {
            sendFailure(player, "OPEN", created.status(), created.code(), created.message());
            return;
        }
        sendCatalog(player);
        // An existing draft is deliberately reused; opening it still passes through
        // the normal permission, ownership, revision, and lease checks below.
        open(player, bookId.toString());
    }

    private static void renew(ServerPlayer player, String rawSessionId, String draftRevision) {
        UUID sessionId = parseUuid(rawSessionId);
        if (sessionId == null) {
            sendFailure(player, "RENEW", AuthorOperationResult.Status.INVALID_REQUEST,
                    "INVALID_SESSION_ID", "Invalid edit-session ID");
            return;
        }
        AuthorOperationResult<EditSessionHandle> result = AuthorApi.renew(player, sessionId, draftRevision);
        if (!result.success()) {
            sendFailure(player, "RENEW", result.status(), result.code(), result.message());
            return;
        }
        sendSession(player, "RENEW", result.status(), result.code(), result.message(), result.value(), 0, 0);
    }

    private static void close(ServerPlayer player, String rawSessionId, String draftRevision) {
        UUID sessionId = parseUuid(rawSessionId);
        if (sessionId == null) {
            sendFailure(player, "CLOSE", AuthorOperationResult.Status.INVALID_REQUEST,
                    "INVALID_SESSION_ID", "Invalid edit-session ID");
            return;
        }
        AuthorOperationResult<EditSessionView> result = AuthorApi.close(player, sessionId, draftRevision);
        if (!result.success()) {
            sendFailure(player, "CLOSE", result.status(), result.code(), result.message());
            return;
        }
        EditSessionView view = result.value();
        EditSessionHandle handle = new EditSessionHandle(sessionId, view);
        sendSession(player, "CLOSE", result.status(), result.code(), result.message(), handle, 0, 0);
    }

    private static void save(ServerPlayer player, String rawSessionId, String rawBookId, String draftRevision) {
        UUID sessionId = parseUuid(rawSessionId);
        ResourceLocation bookId = ResourceLocation.tryParse(rawBookId);
        if (sessionId == null || bookId == null || draftRevision == null || draftRevision.isBlank()) {
            sendFailure(player, "SAVE", AuthorOperationResult.Status.INVALID_REQUEST,
                    "INVALID_SAVE_REQUEST", "Incomplete draft save request");
            return;
        }
        var saved = AuthorApi.save(player, sessionId, bookId, draftRevision);
        if (!saved.success()) {
            String message = saved.message();
            if (saved.value() != null && !saved.value().diagnostics().isEmpty()) {
                var first = saved.value().diagnostics().getFirst();
                message += ": " + first.code() + " " + first.message();
            }
            sendFailure(player, "SAVE", saved.status(), saved.code(), message);
            return;
        }
        var renewed = AuthorApi.renew(player, sessionId, draftRevision);
        if (!renewed.success()) {
            sendFailure(player, "SAVE", renewed.status(), renewed.code(), renewed.message());
            return;
        }
        sendSession(player, "SAVE", saved.status(), saved.code(), saved.message(), renewed.value(), 0, 0);
    }

    private static void updateQuest(ServerPlayer player, String json) {
        QuestUpdateWire wire;
        try {
            wire = GSON.fromJson(json, QuestUpdateWire.class);
        } catch (RuntimeException exception) {
            sendFailure(player, "UPDATE", AuthorOperationResult.Status.INVALID_REQUEST,
                    "INVALID_QUEST_UPDATE", "Malformed quest update request");
            return;
        }
        UUID sessionId = wire == null ? null : parseUuid(wire.sessionId());
        ResourceLocation bookId = wire == null ? null : ResourceLocation.tryParse(wire.bookId());
        ResourceLocation questId = wire == null ? null : ResourceLocation.tryParse(wire.questId());
        if (sessionId == null || bookId == null || questId == null || wire.draftRevision() == null
                || wire.title() == null || wire.subtitle() == null || wire.description() == null) {
            sendFailure(player, "UPDATE", AuthorOperationResult.Status.INVALID_REQUEST,
                    "INVALID_QUEST_UPDATE", "Incomplete quest update request");
            return;
        }
        AuthorOperationResult<DraftSnapshot> current = EditSessionService.get().snapshot(player, sessionId,
                bookId, wire.draftRevision());
        QuestDefinition quest = current.success() ? current.value().book().quests().stream()
                .filter(candidate -> candidate.id().equals(questId)).findFirst().orElse(null) : null;
        if (!current.success() || quest == null) {
            sendFailure(player, "UPDATE", current.success() ? AuthorOperationResult.Status.NOT_FOUND : current.status(),
                    current.success() ? "QUEST_NOT_FOUND" : current.code(),
                    current.success() ? "Selected quest no longer exists" : current.message());
            return;
        }
        QuestDefinition replacement = new QuestDefinition(quest.bookId(), quest.id(), quest.chapterId(),
                wire.title(), wire.subtitle(), wire.description(), quest.icon(), quest.x(), quest.y(),
                quest.dependencies(), quest.tasks(), quest.rewards(), quest.legacyId());
        var updated = AuthorApi.editor().updateQuest(player, sessionId, bookId, wire.draftRevision(), questId,
                replacement);
        if (!updated.success()) {
            String message = updated.message();
            if (updated.value() != null && !updated.value().diagnostics().isEmpty()) {
                var first = updated.value().diagnostics().getFirst();
                message += ": " + first.code() + " " + first.message();
            }
            sendFailure(player, "UPDATE", updated.status(), updated.code(), message);
            return;
        }
        DraftSnapshot draft = updated.value().snapshot();
        var renewed = AuthorApi.renew(player, sessionId, draft.draftRevision());
        if (!renewed.success()) {
            sendFailure(player, "UPDATE", renewed.status(), renewed.code(), renewed.message());
            return;
        }
        // Property completion updates the authoritative session; the visible Save
        // control is the explicit boundary that persists the accumulated draft.
        sendDraft(player, "UPDATE", "QUEST_UPDATED_UNSAVED",
                "Quest properties updated; save the draft to persist them", renewed.value(), draft);
    }

    private static void mutate(ServerPlayer player, String json) {
        EditorMutationWire wire;
        try {
            wire = GSON.fromJson(json, EditorMutationWire.class);
        } catch (RuntimeException exception) {
            sendFailure(player, "MUTATE", AuthorOperationResult.Status.INVALID_REQUEST,
                    "INVALID_EDITOR_MUTATION", "Malformed editor mutation request");
            return;
        }
        UUID sessionId = wire == null ? null : parseUuid(wire.sessionId());
        ResourceLocation bookId = wire == null ? null : ResourceLocation.tryParse(wire.bookId());
        if (sessionId == null || bookId == null || wire.draftRevision() == null || wire.action() == null) {
            sendFailure(player, "MUTATE", AuthorOperationResult.Status.INVALID_REQUEST,
                    "INVALID_EDITOR_MUTATION", "Incomplete editor mutation request");
            return;
        }
        var current = EditSessionService.get().snapshot(player, sessionId, bookId, wire.draftRevision());
        if (!current.success()) {
            sendFailure(player, "MUTATE", current.status(), current.code(), current.message());
            return;
        }
        ResourceLocation targetId = ResourceLocation.tryParse(wire.targetId());
        ResourceLocation parentId = ResourceLocation.tryParse(wire.parentId());
        ResourceLocation sourceId = ResourceLocation.tryParse(wire.sourceId());
        var editor = AuthorApi.editor();
        AuthorOperationResult<DraftEditResult> result;
        try {
            result = switch (wire.action()) {
                case "ADD_GROUP" -> editor.addGroup(player, sessionId, bookId, wire.draftRevision(),
                        new ChapterGroupDefinition(bookId, requireId(targetId), boundedTitle(wire.title()), wire.targetIndex()));
                case "UPDATE_GROUP" -> editor.updateGroup(player, sessionId, bookId, wire.draftRevision(),
                        requireId(targetId), new ChapterGroupDefinition(bookId, targetId,
                                boundedTitle(wire.title()), wire.targetIndex()));
                case "MOVE_GROUP" -> editor.moveGroup(player, sessionId, bookId, wire.draftRevision(),
                        requireId(targetId), wire.targetIndex());
                case "DELETE_GROUP" -> editor.removeGroupWithContents(player, sessionId, bookId,
                        wire.draftRevision(), requireId(targetId));
                case "ADD_CHAPTER" -> editor.addChapter(player, sessionId, bookId, wire.draftRevision(),
                        new ChapterDefinition(bookId, requireId(targetId), requireId(parentId),
                                boundedTitle(wire.title()), "", wire.targetIndex(), List.of()));
                case "UPDATE_CHAPTER" -> editor.updateChapter(player, sessionId, bookId, wire.draftRevision(),
                        requireId(targetId), chapterReplacement(current.value().book(), targetId, parentId,
                                boundedTitle(wire.title()), wire.targetIndex()));
                case "MOVE_CHAPTER" -> editor.moveChapterOrder(player, sessionId, bookId, wire.draftRevision(),
                        requireId(targetId), wire.targetIndex());
                case "DELETE_CHAPTER" -> editor.removeChapterWithContents(player, sessionId, bookId,
                        wire.draftRevision(), requireId(targetId));
                case "ADD_QUEST" -> editor.addQuest(player, sessionId, bookId, wire.draftRevision(),
                        requireId(parentId), new QuestDefinition(bookId, requireId(targetId), parentId,
                                boundedTitle(wire.title()), "", "", "", wire.x(), wire.y(),
                                List.of(), List.of(), List.of(), ""));
                case "COPY_QUEST" -> editor.copyQuest(player, sessionId, bookId, wire.draftRevision(),
                        requireId(sourceId), questCopy(current.value().book(), sourceId, requireId(targetId),
                                boundedTitle(wire.title()), wire.x(), wire.y()));
                case "DELETE_QUEST" -> editor.removeQuestAndReferences(player, sessionId, bookId,
                        wire.draftRevision(), requireId(targetId));
                case "MOVE_QUESTS" -> editor.updateQuestPositions(player, sessionId, bookId, wire.draftRevision(),
                        decodePositions(wire.positions()));
                default -> AuthorOperationResult.failure(AuthorOperationResult.Status.INVALID_REQUEST,
                        "UNKNOWN_EDITOR_MUTATION", "Unknown editor mutation action");
            };
        } catch (IllegalArgumentException exception) {
            sendFailure(player, "MUTATE", AuthorOperationResult.Status.INVALID_REQUEST,
                    "INVALID_EDITOR_MUTATION", exception.getMessage());
            return;
        }
        sendMutationResult(player, sessionId, bookId, result);
    }

    private static void sendMutationResult(ServerPlayer player, UUID sessionId, ResourceLocation bookId,
                                           AuthorOperationResult<DraftEditResult> result) {
        if (!result.success()) {
            String message = result.message();
            if (result.value() != null && !result.value().diagnostics().isEmpty()) {
                var first = result.value().diagnostics().getFirst();
                message += ": " + first.code() + " " + first.message();
            }
            sendFailure(player, "MUTATE", result.status(), result.code(), message);
            return;
        }
        DraftSnapshot draft = result.value().snapshot();
        var renewed = AuthorApi.renew(player, sessionId, draft.draftRevision());
        if (!renewed.success()) {
            sendFailure(player, "MUTATE", renewed.status(), renewed.code(), renewed.message());
            return;
        }
        sendDraft(player, "MUTATE", result.code(), result.message(), renewed.value(), draft);
    }

    private static ResourceLocation requireId(ResourceLocation id) {
        if (id == null) throw new IllegalArgumentException("A valid namespaced ID is required");
        return id;
    }

    private static ChapterDefinition chapterReplacement(yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition book,
                                                        ResourceLocation chapterId, ResourceLocation groupId,
                                                        String title, int order) {
        ChapterDefinition chapter = book.chapters().stream().filter(value -> value.id().equals(chapterId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Chapter no longer exists"));
        return new ChapterDefinition(book.id(), chapter.id(), requireId(groupId), title,
                chapter.icon(), order, chapter.quests());
    }

    private static QuestDefinition questCopy(yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition book,
                                             ResourceLocation sourceId, ResourceLocation targetId,
                                             String title, double x, double y) {
        QuestDefinition source = book.quests().stream().filter(value -> value.id().equals(sourceId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Source quest no longer exists"));
        List<TaskDefinition> tasks = new java.util.ArrayList<>();
        for (int index = 0; index < source.tasks().size(); index++) {
            TaskDefinition task = source.tasks().get(index);
            tasks.add(new TaskDefinition(book.id(), nestedCopyId(book, targetId, "task", index),
                    task.typeId(), task.config(), task.optional()));
        }
        List<RewardDefinition> rewards = new java.util.ArrayList<>();
        for (int index = 0; index < source.rewards().size(); index++) {
            RewardDefinition reward = source.rewards().get(index);
            rewards.add(new RewardDefinition(book.id(), nestedCopyId(book, targetId, "reward", index),
                    reward.typeId(), reward.config(), reward.claimPolicy(), reward.teamReward()));
        }
        return new QuestDefinition(book.id(), targetId, source.chapterId(), title, source.subtitle(),
                source.description(), source.icon(), x, y, source.dependencies(), tasks, rewards, "");
    }

    private static ResourceLocation nestedCopyId(yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition book,
                                                 ResourceLocation targetId, String kind, int index) {
        for (int suffix = 0; suffix < 10_000; suffix++) {
            String tail = suffix == 0 ? "" : "_" + suffix;
            ResourceLocation candidate = ResourceLocation.fromNamespaceAndPath(targetId.getNamespace(),
                    targetId.getPath() + "/" + kind + "_" + index + tail);
            boolean exists = book.quests().stream().flatMap(quest -> java.util.stream.Stream.concat(
                            quest.tasks().stream().map(TaskDefinition::id), quest.rewards().stream().map(RewardDefinition::id)))
                    .anyMatch(candidate::equals);
            if (!exists) return candidate;
        }
        throw new IllegalArgumentException("Unable to allocate copied " + kind + " ID");
    }

    private static Map<ResourceLocation, DraftBookEditor.Position> decodePositions(List<PositionWire> wires) {
        if (wires == null || wires.isEmpty() || wires.size() > BrnQuestConstants.MAX_QUESTS) {
            throw new IllegalArgumentException("Moved-node list is empty or exceeds the editor limit");
        }
        Map<ResourceLocation, DraftBookEditor.Position> positions = new LinkedHashMap<>();
        for (PositionWire wire : wires) {
            ResourceLocation id = wire == null ? null : ResourceLocation.tryParse(wire.questId());
            if (id == null || !Double.isFinite(wire.x()) || !Double.isFinite(wire.y())
                    || positions.putIfAbsent(id, new DraftBookEditor.Position(wire.x(), wire.y())) != null) {
                throw new IllegalArgumentException("Moved-node entries require unique IDs and finite coordinates");
            }
        }
        return Map.copyOf(positions);
    }

    private static void sendOpened(ServerPlayer player, EditSessionHandle handle, DraftSnapshot draft) {
        sendDraft(player, "OPEN", "SESSION_OPENED", "Edit session opened", handle, draft);
    }

    private static void sendDraft(ServerPlayer player, String action, String code, String message,
                                  EditSessionHandle handle, DraftSnapshot draft) {
        String json = NativeBookJson.encode(draft.book());
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > BrnQuestConstants.MAX_BOOK_BYTES
                || draft.book().quests().size() > BrnQuestConstants.MAX_QUESTS) {
            AuthorApi.close(player, handle.sessionId(), handle.session().draftRevision());
            sendFailure(player, action, AuthorOperationResult.Status.INVALID_REQUEST,
                    "DRAFT_TOO_LARGE", "Draft exceeds the editor protocol limits");
            return;
        }
        List<String> chunks = BrnQuestNetwork.split(json, BrnQuestNetwork.BOOK_CHUNK_CHARACTERS);
        sendSession(player, action, AuthorOperationResult.Status.SUCCESS, code,
                message, handle, chunks.size(), bytes.length);
        for (int index = 0; index < chunks.size(); index++) {
            BrnQuestNetwork.send(player, new DraftChunkPayload(handle.sessionId().toString(),
                    draft.draftRevision(), index, chunks.get(index)));
        }
    }

    private static void sendSession(ServerPlayer player, String action, AuthorOperationResult.Status status,
                                    String code, String message, EditSessionHandle handle,
                                    int chunks, int decodedBytes) {
        EditSessionView view = handle.session();
        long remaining = Math.max(0L, view.expiresAtTick() - player.getServer().getTickCount());
        SessionResponseWire response = new SessionResponseWire(action, status.name(), code,
                boundedMessage(message), handle.sessionId().toString(), view.bookId().toString(),
                view.baseRevision(), view.draftRevision(), view.savedRevision(), remaining, chunks, decodedBytes);
        BrnQuestNetwork.send(player, new SessionPayload(GSON.toJson(response)));
    }

    private static void sendFailure(ServerPlayer player, String action, AuthorOperationResult.Status status,
                                    String code, String message) {
        SessionResponseWire response = new SessionResponseWire(action, status.name(), code,
                boundedMessage(message), "", "", "", "", "", 0L, 0, 0);
        BrnQuestNetwork.send(player, new SessionPayload(GSON.toJson(response)));
    }

    private static String boundedMessage(String message) {
        String value = message == null ? "" : message;
        return value.length() <= 512 ? value : value.substring(0, 512);
    }

    private static String boundedTitle(String title) {
        String value = title == null ? "" : title;
        return value.length() <= 256 ? value : value.substring(0, 256);
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(BRNQuest.MOD_ID, path));
    }

    private static <T extends CustomPacketPayload> void registerClient(PayloadRegistrar registrar,
                                                                        CustomPacketPayload.Type<T> type,
                                                                        StreamCodec<? super ByteBuf, T> codec,
                                                                        net.neoforged.neoforge.network.handling.IPayloadHandler<T> handler) {
        if (FMLEnvironment.dist == Dist.CLIENT) registrar.playToClient(type, codec, handler);
        else registrar.playToClient(type, codec, (payload, context) -> {});
    }

    private static final class ClientDelegate {
        static void catalog(CatalogPayload payload,
                            net.neoforged.neoforge.network.handling.IPayloadContext context) {
            ClientPayloadHandler.editorCatalog(payload);
        }

        static void session(SessionPayload payload,
                            net.neoforged.neoforge.network.handling.IPayloadContext context) {
            ClientPayloadHandler.editorSession(payload);
        }

        static void draftChunk(DraftChunkPayload payload,
                               net.neoforged.neoforge.network.handling.IPayloadContext context) {
            ClientPayloadHandler.editorDraftChunk(payload);
        }
    }
}
