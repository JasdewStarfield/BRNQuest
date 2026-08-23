package yourscraft.jasdewstarfield.brnquest.network;

import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
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
import yourscraft.jasdewstarfield.brnquest.data.QuestBookDefinition;
import yourscraft.jasdewstarfield.brnquest.data.QuestIconValue;
import yourscraft.jasdewstarfield.brnquest.data.RewardDefinition;
import yourscraft.jasdewstarfield.brnquest.data.TaskDefinition;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;
import yourscraft.jasdewstarfield.brnquest.task.TaskTypes;

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

    /** One confirmed request drives the existing save, publish, backup-deploy, and reload boundaries. */
    public record PublishApplyPayload(String sessionId, String bookId, String draftRevision)
            implements CustomPacketPayload {
        public static final Type<PublishApplyPayload> TYPE = AuthoringNetwork.type("editor_publish_apply");
        public static final StreamCodec<ByteBuf, PublishApplyPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, PublishApplyPayload::sessionId,
                ByteBufCodecs.STRING_UTF8, PublishApplyPayload::bookId,
                ByteBufCodecs.STRING_UTF8, PublishApplyPayload::draftRevision,
                PublishApplyPayload::new);
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

    public record EditorDiagnosticWire(String severity, String code, String objectId,
                                       String path, String message) {}

    public record SessionResponseWire(String action, String status, String code, String message,
                                      String sessionId, String bookId, String baseRevision,
                                      String draftRevision, String savedRevision, long remainingTicks,
                                      int chunks, int decodedBytes, int undoSteps, int redoSteps,
                                      List<EditorDiagnosticWire> diagnostics) {
        public SessionResponseWire {
            undoSteps = Math.max(0, undoSteps);
            redoSteps = Math.max(0, redoSteps);
            diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        }

        /** Retains the protocol-2 constructor used by existing callers and additive-response tests. */
        public SessionResponseWire(String action, String status, String code, String message,
                                   String sessionId, String bookId, String baseRevision,
                                   String draftRevision, String savedRevision, long remainingTicks,
                                   int chunks, int decodedBytes) {
            this(action, status, code, message, sessionId, bookId, baseRevision, draftRevision,
                    savedRevision, remainingTicks, chunks, decodedBytes, 0, 0, List.of());
        }

        /** Keeps structured-diagnostic callers source-compatible with the additive history fields. */
        public SessionResponseWire(String action, String status, String code, String message,
                                   String sessionId, String bookId, String baseRevision,
                                   String draftRevision, String savedRevision, long remainingTicks,
                                   int chunks, int decodedBytes, List<EditorDiagnosticWire> diagnostics) {
            this(action, status, code, message, sessionId, bookId, baseRevision, draftRevision,
                    savedRevision, remainingTicks, chunks, decodedBytes, 0, 0, diagnostics);
        }
    }

    public record QuestUpdateWire(String sessionId, String bookId, String draftRevision, String questId,
                                  String replacementQuestId, String title, String subtitle,
                                  String description, String iconKind, String iconValue, boolean preserveIcon) {}

    public record PositionWire(String questId, double x, double y) {}

    public record EditorMutationWire(String sessionId, String bookId, String draftRevision, String action,
                                     String targetId, String parentId, String sourceId, String title,
                                     int targetIndex, double x, double y, List<PositionWire> positions,
                                     Map<String, String> config) {}

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
        registrar.playToServer(PublishApplyPayload.TYPE, PublishApplyPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                publishAndApply(player, payload.sessionId(), payload.bookId(), payload.draftRevision());
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

    public static void publishAndApply(UUID sessionId, ResourceLocation bookId, String draftRevision) {
        PacketDistributor.sendToServer(new PublishApplyPayload(sessionId.toString(), bookId.toString(), draftRevision));
    }

    public static void updateQuest(UUID sessionId, ResourceLocation bookId, String draftRevision,
                                   ResourceLocation questId, ResourceLocation replacementQuestId,
                                   String title, String subtitle, String description, String iconKind, String iconValue,
                                   boolean preserveIcon) {
        QuestUpdateWire wire = new QuestUpdateWire(sessionId.toString(), bookId.toString(), draftRevision,
                questId.toString(), replacementQuestId.toString(), title, subtitle, description,
                iconKind, iconValue, preserveIcon);
        PacketDistributor.sendToServer(new UpdateQuestPayload(GSON.toJson(wire)));
    }

    public static void mutate(EditorMutationWire wire) {
        PacketDistributor.sendToServer(new EditorMutationPayload(GSON.toJson(wire)));
    }

    /** Undo and redo reuse the bounded mutation envelope but carry no client-owned draft content. */
    public static void history(UUID sessionId, ResourceLocation bookId, String draftRevision, boolean redo) {
        mutate(new EditorMutationWire(sessionId.toString(), bookId.toString(), draftRevision,
                redo ? "REDO" : "UNDO", "", "", "", "", 0, 0.0D, 0.0D, List.of(), Map.of()));
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
            if (saved.value() != null && saved.value().revisionCheck() != null
                    && saved.value().revisionCheck().hasConflicts()) {
                var first = saved.value().revisionCheck().conflicts().getFirst();
                message += ": expected " + shortRevision(first.expectedRevision())
                        + ", actual " + shortRevision(first.actualRevision());
            } else if (saved.value() != null && !saved.value().diagnostics().isEmpty()) {
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

    /**
     * Runs the author-facing one-stop operation without weakening any existing server-side gate.
     * Later-stage failures explicitly report that earlier durable stages may already have completed.
     */
    private static void publishAndApply(ServerPlayer player, String rawSessionId, String rawBookId,
                                        String draftRevision) {
        UUID sessionId = parseUuid(rawSessionId);
        ResourceLocation bookId = ResourceLocation.tryParse(rawBookId);
        if (sessionId == null || bookId == null || draftRevision == null || draftRevision.isBlank()) {
            sendFailure(player, "PUBLISH", AuthorOperationResult.Status.INVALID_REQUEST,
                    "INVALID_PUBLISH_REQUEST", "Incomplete publish request");
            return;
        }
        debugPublishPhase(player, bookId, draftRevision, "request", "STARTED", "PUBLISH_REQUEST_ACCEPTED");
        var saved = AuthorApi.save(player, sessionId, bookId, draftRevision);
        debugPublishPhase(player, bookId, draftRevision, "save", saved.status().name(), saved.code());
        if (!saved.success()) {
            sendFailure(player, "PUBLISH", saved.status(), saved.code(), "Save failed: " + saved.message());
            return;
        }
        var published = AuthorApi.publish(player, sessionId, bookId, draftRevision);
        debugPublishPhase(player, bookId, draftRevision, "workspace", published.status().name(), published.code());
        if (!published.success()) {
            String message = "Draft was saved, but publish failed: " + published.message();
            if (published.value() != null && published.value().revisionCheck() != null
                    && published.value().revisionCheck().hasConflicts()) {
                var first = published.value().revisionCheck().conflicts().getFirst();
                message += ": expected " + shortRevision(first.expectedRevision())
                        + ", actual " + shortRevision(first.actualRevision());
            } else if (published.value() != null && !published.value().diagnostics().isEmpty()) {
                var first = published.value().diagnostics().getFirst();
                message += ": " + first.code() + " " + first.objectId() + " — " + first.message();
            }
            sendFailure(player, "PUBLISH", published.status(), published.code(),
                    message);
            return;
        }
        var deployed = AuthorApi.deploy(player, true);
        debugPublishPhase(player, bookId, draftRevision, "deploy", deployed.status().name(), deployed.code());
        if (!deployed.success()) {
            sendFailure(player, "PUBLISH", deployed.status(), deployed.code(),
                    "Workspace publish completed, but deployment failed: " + deployed.message());
            return;
        }
        var renewed = AuthorApi.renew(player, sessionId, draftRevision);
        debugPublishPhase(player, bookId, draftRevision, "renew", renewed.status().name(), renewed.code());
        if (!renewed.success()) {
            sendFailure(player, "PUBLISH", renewed.status(), renewed.code(),
                    "Workspace was deployed, but the edit lease could not be renewed: " + renewed.message());
            return;
        }
        AuthorApi.reload(player).whenComplete((reloaded, error) -> player.getServer().execute(() -> {
            if (error != null) {
                debugPublishPhase(player, bookId, draftRevision, "reload", "IO_FAILURE", "RELOAD_FAILED");
                sendFailure(player, "PUBLISH", AuthorOperationResult.Status.IO_FAILURE, "RELOAD_FAILED",
                        "Workspace was deployed, but reload failed: " + error.getMessage());
            } else if (!reloaded.success()) {
                debugPublishPhase(player, bookId, draftRevision, "reload",
                        reloaded.status().name(), reloaded.code());
                sendFailure(player, "PUBLISH", reloaded.status(), reloaded.code(),
                        "Workspace was deployed, but reload failed: " + reloaded.message());
            } else {
                debugPublishPhase(player, bookId, draftRevision, "reload",
                        reloaded.status().name(), "PUBLISH_APPLY_COMPLETE");
                sendSession(player, "PUBLISH", AuthorOperationResult.Status.SUCCESS, "PUBLISH_APPLY_COMPLETE",
                        "Draft published, deployed with backup, and reloaded", renewed.value(), 0, 0);
            }
        }));
    }

    /** Keeps detailed pipeline telemetry available without adding noise to normal INFO logs. */
    private static void debugPublishPhase(ServerPlayer player, ResourceLocation bookId, String revision,
                                          String phase, String status, String code) {
        BRNQuest.LOGGER.debug("[BRNQuest/EDITOR] actor={} book={} revision={} phase={} status={} code={}",
                player.getGameProfile().getName(), bookId, shortRevision(revision), phase, status, code);
    }

    /** Keeps revision diagnostics readable in the fixed-height editor status bar. */
    private static String shortRevision(String revision) {
        if (revision == null || revision.isBlank()) return "<none>";
        return revision.length() <= 12 ? revision : revision.substring(0, 12);
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
        ResourceLocation replacementQuestId = wire == null ? null : ResourceLocation.tryParse(wire.replacementQuestId());
        if (sessionId == null || bookId == null || questId == null || replacementQuestId == null
                || wire.draftRevision() == null || wire.title() == null || wire.subtitle() == null
                || wire.description() == null || wire.iconKind() == null || wire.iconValue() == null) {
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
        String icon = quest.icon();
        if (!wire.preserveIcon()) {
            ResourceLocation iconId = wire.iconValue().isBlank() ? null : ResourceLocation.tryParse(wire.iconValue());
            if ("ITEM".equals(wire.iconKind())) {
                if (!wire.iconValue().isBlank() && (iconId == null || !BuiltInRegistries.ITEM.containsKey(iconId))) {
                    sendFailure(player, "UPDATE", AuthorOperationResult.Status.INVALID_REQUEST,
                            "INVALID_ICON_ITEM", "Icon item must be a registered item ID");
                    return;
                }
                icon = iconId == null ? "" : "{id:\"" + iconId + "\",count:1}";
            } else if ("TEXTURE".equals(wire.iconKind())) {
                if (!wire.iconValue().isBlank() && iconId == null) {
                    sendFailure(player, "UPDATE", AuthorOperationResult.Status.INVALID_REQUEST,
                            "INVALID_ICON_TEXTURE", "Icon texture must be a ResourceLocation");
                    return;
                }
                icon = iconId == null ? "" : QuestIconValue.texture(iconId);
            } else {
                sendFailure(player, "UPDATE", AuthorOperationResult.Status.INVALID_REQUEST,
                        "INVALID_ICON_KIND", "Unknown quest icon kind");
                return;
            }
        }
        QuestDefinition replacement = new QuestDefinition(quest.bookId(), replacementQuestId, quest.chapterId(),
                wire.title(), wire.subtitle(), wire.description(), icon, quest.x(), quest.y(),
                quest.dependencies(), quest.tasks(), quest.rewards(), quest.legacyId());
        var updated = AuthorApi.editor().updateQuestBasics(player, sessionId, bookId, wire.draftRevision(), questId,
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
        ResourceLocation targetId = parseId(wire.targetId());
        ResourceLocation parentId = parseId(wire.parentId());
        ResourceLocation sourceId = parseId(wire.sourceId());
        var editor = AuthorApi.editor();
        AuthorOperationResult<DraftEditResult> result;
        try {
            result = switch (wire.action()) {
                case "UNDO" -> EditSessionService.get().undo(player, sessionId, bookId, wire.draftRevision());
                case "REDO" -> EditSessionService.get().redo(player, sessionId, bookId, wire.draftRevision());
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
                case "ADD_DEPENDENCY" -> editor.addDependency(player, sessionId, bookId, wire.draftRevision(),
                        requireId(targetId), requireId(sourceId));
                case "REMOVE_DEPENDENCY" -> editor.removeDependency(player, sessionId, bookId,
                        wire.draftRevision(), requireId(targetId), requireId(sourceId));
                case "ADD_TASK" -> editor.addTask(player, sessionId, bookId, wire.draftRevision(),
                        requireId(parentId), new TaskDefinition(bookId, requireId(targetId), requireId(sourceId),
                                typedMutationConfig(player, sourceId, wire.config()), false));
                case "UPDATE_TASK" -> editor.updateTask(player, sessionId, bookId, wire.draftRevision(),
                        requireId(parentId), requireId(sourceId), taskReplacement(player, current.value().book(),
                                parentId, sourceId, requireId(targetId), wire.config(), wire.targetIndex() != 0));
                case "COPY_TASK" -> editor.copyTask(player, sessionId, bookId, wire.draftRevision(),
                        requireId(parentId), requireId(sourceId), taskCopy(current.value().book(),
                                parentId, sourceId, requireId(targetId)));
                case "MOVE_TASK" -> editor.moveTask(player, sessionId, bookId, wire.draftRevision(),
                        requireId(parentId), requireId(targetId), wire.targetIndex());
                case "DELETE_TASK" -> editor.removeTask(player, sessionId, bookId, wire.draftRevision(),
                        requireId(parentId), requireId(targetId));
                case "ADD_REWARD" -> editor.addReward(player, sessionId, bookId, wire.draftRevision(),
                        requireId(parentId), new RewardDefinition(bookId, requireId(targetId), requireId(sourceId),
                                typedMutationConfig(player, sourceId, wire.config()), "manual", false));
                case "UPDATE_REWARD" -> editor.updateReward(player, sessionId, bookId, wire.draftRevision(),
                        requireId(parentId), requireId(sourceId), rewardReplacement(player, current.value().book(),
                                parentId, sourceId, requireId(targetId), wire.config(), boundedClaimPolicy(wire.title()),
                                wire.targetIndex() != 0));
                case "COPY_REWARD" -> editor.copyReward(player, sessionId, bookId, wire.draftRevision(),
                        requireId(parentId), requireId(sourceId), rewardCopy(current.value().book(),
                                parentId, sourceId, requireId(targetId)));
                case "MOVE_REWARD" -> editor.moveReward(player, sessionId, bookId, wire.draftRevision(),
                        requireId(parentId), requireId(targetId), wire.targetIndex());
                case "DELETE_REWARD" -> editor.removeReward(player, sessionId, bookId, wire.draftRevision(),
                        requireId(parentId), requireId(targetId));
                default -> AuthorOperationResult.failure(AuthorOperationResult.Status.INVALID_REQUEST,
                        "UNKNOWN_EDITOR_MUTATION", "Unknown editor mutation action");
            };
        } catch (IllegalArgumentException exception) {
            sendFailure(player, "MUTATE", AuthorOperationResult.Status.INVALID_REQUEST,
                    "INVALID_EDITOR_MUTATION", exception.getMessage(), List.of(mutationDiagnostic(wire, exception)));
            return;
        }
        sendMutationResult(player, sessionId, bookId, result, wire);
    }

    private static void sendMutationResult(ServerPlayer player, UUID sessionId, ResourceLocation bookId,
                                           AuthorOperationResult<DraftEditResult> result, EditorMutationWire wire) {
        if (!result.success()) {
            String message = result.message();
            List<EditorDiagnosticWire> diagnostics = List.of();
            if (result.value() != null && !result.value().diagnostics().isEmpty()) {
                var first = result.value().diagnostics().getFirst();
                message += ": " + first.code() + " " + first.message();
                diagnostics = mutationDiagnosticWires(wire, result.value().diagnostics());
            } else if ("UPDATE_TASK".equals(wire.action()) || "UPDATE_REWARD".equals(wire.action())) {
                String path = "DUPLICATE_TYPED_ID".equals(result.code()) ? "id" : "";
                diagnostics = List.of(new EditorDiagnosticWire("ERROR", result.code(),
                        wire.sourceId(), path, result.message()));
            }
            sendFailure(player, "MUTATE", result.status(), result.code(), message, diagnostics);
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

    /** Optional mutation slots use empty strings; malformed clients may omit them entirely. */
    private static ResourceLocation parseId(String raw) {
        return raw == null || raw.isBlank() ? null : ResourceLocation.tryParse(raw);
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

    /** Copies opaque extension data on the server; the client never reconstructs unknown task config. */
    private static TaskDefinition taskCopy(QuestBookDefinition book, ResourceLocation questId,
                                           ResourceLocation sourceId, ResourceLocation targetId) {
        QuestDefinition quest = requireQuest(book, questId);
        TaskDefinition source = quest.tasks().stream().filter(task -> task.id().equals(sourceId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Source task no longer exists"));
        return new TaskDefinition(book.id(), targetId, source.typeId(), source.config(), source.optional());
    }

    /** Copies claim semantics and opaque extension data without client-side decoding. */
    private static RewardDefinition rewardCopy(QuestBookDefinition book, ResourceLocation questId,
                                                ResourceLocation sourceId, ResourceLocation targetId) {
        QuestDefinition quest = requireQuest(book, questId);
        RewardDefinition source = quest.rewards().stream().filter(reward -> reward.id().equals(sourceId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Source reward no longer exists"));
        return new RewardDefinition(book.id(), targetId, source.typeId(), source.config(),
                source.claimPolicy(), source.teamReward());
    }

    private static QuestDefinition requireQuest(QuestBookDefinition book, ResourceLocation questId) {
        if (questId == null) throw new IllegalArgumentException("Quest ID is required");
        return book.quests().stream().filter(quest -> quest.id().equals(questId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Selected quest no longer exists"));
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
                view.baseRevision(), view.draftRevision(), view.savedRevision(), remaining, chunks, decodedBytes,
                view.undoSteps(), view.redoSteps(), List.of());
        BrnQuestNetwork.send(player, new SessionPayload(GSON.toJson(response)));
    }

    private static void sendFailure(ServerPlayer player, String action, AuthorOperationResult.Status status,
                                    String code, String message) {
        sendFailure(player, action, status, code, message, List.of());
    }

    private static void sendFailure(ServerPlayer player, String action, AuthorOperationResult.Status status,
                                    String code, String message, List<EditorDiagnosticWire> diagnostics) {
        SessionResponseWire response = new SessionResponseWire(action, status.name(), code,
                boundedMessage(message), "", "", "", "", "", 0L, 0, 0, diagnostics);
        BrnQuestNetwork.send(player, new SessionPayload(GSON.toJson(response)));
    }

    private static EditorDiagnosticWire mutationDiagnostic(EditorMutationWire wire,
                                                           IllegalArgumentException exception) {
        String message = exception.getMessage() == null ? "Invalid editor mutation" : exception.getMessage();
        String path = message.startsWith("Item config") ? "config.item"
                : message.startsWith("Reward claim policy") ? "claim_policy" : "";
        return new EditorDiagnosticWire("ERROR", "INVALID_EDITOR_MUTATION",
                wire.sourceId() == null ? "" : wire.sourceId(), path, boundedMessage(message));
    }

    private static List<EditorDiagnosticWire> diagnosticWires(
            List<yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic> diagnostics) {
        return diagnostics.stream().limit(8).map(diagnostic -> new EditorDiagnosticWire(
                diagnostic.severity().name(), diagnostic.code(), boundedMessage(diagnostic.objectId()),
                boundedMessage(diagnostic.path()), boundedMessage(diagnostic.message()))).toList();
    }

    static List<EditorDiagnosticWire> mutationDiagnosticWires(EditorMutationWire wire,
            List<yourscraft.jasdewstarfield.brnquest.diagnostic.Diagnostic> diagnostics) {
        boolean typedUpdate = "UPDATE_TASK".equals(wire.action()) || "UPDATE_REWARD".equals(wire.action());
        return diagnostics.stream().limit(8).map(diagnostic -> {
            String path = diagnostic.path();
            if (typedUpdate && path.isBlank()
                    && ("BQV-119".equals(diagnostic.code()) || "BQV-120".equals(diagnostic.code()))) {
                // Codec errors concern the complete raw map when no descriptor can identify one field.
                path = "config";
            }
            return new EditorDiagnosticWire(diagnostic.severity().name(), diagnostic.code(),
                    boundedMessage(diagnostic.objectId()), boundedMessage(path), boundedMessage(diagnostic.message()));
        }).toList();
    }

    private static String boundedMessage(String message) {
        String value = message == null ? "" : message;
        return value.length() <= 512 ? value : value.substring(0, 512);
    }

    private static String boundedTitle(String title) {
        String value = title == null ? "" : title;
        return value.length() <= 256 ? value : value.substring(0, 256);
    }

    /** Mutation JSON is untrusted even though the complete candidate is validated before commit. */
    static Map<String, String> boundedConfig(Map<String, String> config) {
        if (config == null || config.isEmpty()) return Map.of();
        if (config.size() > 64) throw new IllegalArgumentException("Typed config exceeds 64 fields");
        Map<String, String> bounded = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : config.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || key.isBlank() || key.length() > 128 || value == null || value.length() > 65_536) {
                throw new IllegalArgumentException("Typed config contains an invalid field");
            }
            bounded.put(key, value);
        }
        return Map.copyOf(bounded);
    }

    private static Map<String, String> typedMutationConfig(ServerPlayer player, ResourceLocation typeId,
                                                           Map<String, String> config) {
        Map<String, String> bounded = boundedConfig(config);
        if (!TaskTypes.ITEM.equals(typeId)) return bounded;
        String itemSnbt = bounded.get("item");
        try {
            ItemStack stack = itemSnbt == null ? ItemStack.EMPTY
                    : ItemStack.parseOptional(player.registryAccess(), TagParser.parseTag(itemSnbt));
            if (stack.isEmpty()) throw new IllegalArgumentException("Item config must contain a registered item");
            Map<String, String> normalized = new LinkedHashMap<>(bounded);
            // Quantity belongs to the separate config field; the identity stack stays count one.
            normalized.put("item", stack.copyWithCount(1).save(player.registryAccess()).toString());
            return Map.copyOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Item config contains invalid ItemStack SNBT");
        }
    }

    private static TaskDefinition taskReplacement(ServerPlayer player, QuestBookDefinition book,
                                                  ResourceLocation questId, ResourceLocation sourceId,
                                                  ResourceLocation replacementId, Map<String, String> config,
                                                  boolean optional) {
        QuestDefinition quest = requireQuest(book, questId);
        TaskDefinition source = quest.tasks().stream().filter(task -> task.id().equals(sourceId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Selected task no longer exists"));
        return new TaskDefinition(book.id(), replacementId, source.typeId(),
                typedMutationConfig(player, source.typeId(), config), optional);
    }

    private static RewardDefinition rewardReplacement(ServerPlayer player, QuestBookDefinition book,
                                                       ResourceLocation questId, ResourceLocation sourceId,
                                                       ResourceLocation replacementId, Map<String, String> config,
                                                       String claimPolicy, boolean teamReward) {
        QuestDefinition quest = requireQuest(book, questId);
        RewardDefinition source = quest.rewards().stream().filter(reward -> reward.id().equals(sourceId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Selected reward no longer exists"));
        return new RewardDefinition(book.id(), replacementId, source.typeId(),
                typedMutationConfig(player, source.typeId(), config), claimPolicy, teamReward);
    }

    private static String boundedClaimPolicy(String policy) {
        String value = policy == null ? "" : policy.strip();
        if (value.isEmpty() || value.length() > 64) {
            throw new IllegalArgumentException("Reward claim policy must contain 1-64 characters");
        }
        return value;
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
