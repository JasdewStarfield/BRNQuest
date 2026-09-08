package yourscraft.jasdewstarfield.brnquest.network;

import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.brnquest.BRNQuest;
import yourscraft.jasdewstarfield.brnquest.BrnQuestConstants;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Stable authoring payload/wire contracts and client sends; server routing belongs to the registrar. */
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

    public record OpenCurrentSessionPayload(String bookId, String activeRevision, String draftRevision,
                                            boolean replaceDraft) implements CustomPacketPayload {
        public static final Type<OpenCurrentSessionPayload> TYPE = AuthoringNetwork.type("editor_session_open_current");
        public static final StreamCodec<ByteBuf, OpenCurrentSessionPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, OpenCurrentSessionPayload::bookId,
                ByteBufCodecs.STRING_UTF8, OpenCurrentSessionPayload::activeRevision,
                ByteBufCodecs.STRING_UTF8, OpenCurrentSessionPayload::draftRevision,
                ByteBufCodecs.BOOL, OpenCurrentSessionPayload::replaceDraft,
                OpenCurrentSessionPayload::new);
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

    /** Conflict recovery never accepts a client-owned book body or stale revision as authority. */
    public record RecoverSessionPayload(String json) implements CustomPacketPayload {
        public static final Type<RecoverSessionPayload> TYPE = AuthoringNetwork.type("editor_session_recover");
        public static final StreamCodec<ByteBuf, RecoverSessionPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(BrnQuestConstants.MAX_EDITOR_METADATA_BYTES), RecoverSessionPayload::json,
                RecoverSessionPayload::new);
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

    public record SemanticDiffWire(String kind, String objectKind, String objectId,
                                   String path, String before, String after) {}

    public record PublishReviewWire(boolean publishAllowed, String baseline, String fromRevision,
                                    String targetRevision, String backupStrategy, int diagnosticCount,
                                    int changeCount, boolean truncated, List<EditorDiagnosticWire> diagnostics,
                                    List<SemanticDiffWire> changes) {
        public PublishReviewWire {
            diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
            changes = changes == null ? List.of() : List.copyOf(changes);
        }
    }

    public record SessionResponseWire(String action, String status, String code, String message,
                                      String sessionId, String bookId, String baseRevision,
                                      String draftRevision, String savedRevision, long remainingTicks,
                                      int chunks, int decodedBytes, int undoSteps, int redoSteps,
                                      List<EditorDiagnosticWire> diagnostics, PublishReviewWire review,
                                      List<PositionWire> positionPatch) {
        public SessionResponseWire {
            undoSteps = Math.max(0, undoSteps);
            redoSteps = Math.max(0, redoSteps);
            diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
            positionPatch = positionPatch == null ? List.of() : List.copyOf(positionPatch);
        }

        /** Retains the original authoring constructor used by existing callers and additive-response tests. */
        public SessionResponseWire(String action, String status, String code, String message,
                                   String sessionId, String bookId, String baseRevision,
                                   String draftRevision, String savedRevision, long remainingTicks,
                                   int chunks, int decodedBytes) {
            this(action, status, code, message, sessionId, bookId, baseRevision, draftRevision,
                    savedRevision, remainingTicks, chunks, decodedBytes, 0, 0, List.of(), null, List.of());
        }

        /** Keeps structured-diagnostic callers source-compatible with the additive history fields. */
        public SessionResponseWire(String action, String status, String code, String message,
                                   String sessionId, String bookId, String baseRevision,
                                   String draftRevision, String savedRevision, long remainingTicks,
                                   int chunks, int decodedBytes, List<EditorDiagnosticWire> diagnostics) {
            this(action, status, code, message, sessionId, bookId, baseRevision, draftRevision,
                    savedRevision, remainingTicks, chunks, decodedBytes, 0, 0, diagnostics, null, List.of());
        }

        /** Keeps history-aware callers concise when no publish review is attached. */
        public SessionResponseWire(String action, String status, String code, String message,
                                   String sessionId, String bookId, String baseRevision,
                                   String draftRevision, String savedRevision, long remainingTicks,
                                   int chunks, int decodedBytes, int undoSteps, int redoSteps,
                                   List<EditorDiagnosticWire> diagnostics) {
            this(action, status, code, message, sessionId, bookId, baseRevision, draftRevision,
                    savedRevision, remainingTicks, chunks, decodedBytes, undoSteps, redoSteps,
                    diagnostics, null, List.of());
        }

        /** Keeps publish-review callers source-compatible with the additive position patch. */
        public SessionResponseWire(String action, String status, String code, String message,
                                   String sessionId, String bookId, String baseRevision,
                                   String draftRevision, String savedRevision, long remainingTicks,
                                   int chunks, int decodedBytes, int undoSteps, int redoSteps,
                                   List<EditorDiagnosticWire> diagnostics, PublishReviewWire review) {
            this(action, status, code, message, sessionId, bookId, baseRevision, draftRevision,
                    savedRevision, remainingTicks, chunks, decodedBytes, undoSteps, redoSteps,
                    diagnostics, review, List.of());
        }
    }

    public record QuestUpdateWire(String sessionId, String bookId, String draftRevision, String questId,
                                  String replacementQuestId, String title, String subtitle,
                                  String description, String iconKind, String iconValue, boolean preserveIcon,
                                  Double x, Double y, String shape, Double size, Double iconScale, Double minWidth,
                                  Map<String, String> behavior) {}

    public record PositionWire(String questId, double x, double y) {}

    public record RecoveryWire(String sessionId, String bookId, String action, String targetBookId) {}

    public record EditorMutationWire(String sessionId, String bookId, String draftRevision, String action,
                                     String targetId, String parentId, String sourceId, String title,
                                     int targetIndex, double x, double y, List<PositionWire> positions,
                                     Map<String, String> config) {}

    static void register(PayloadRegistrar registrar) {
        AuthoringPayloadRegistrar.register(registrar);
    }

    /** Client convenience entry points preserve the existing authoring protocol. */
    public static void requestCatalog() {
        PacketDistributor.sendToServer(new RequestCatalogPayload());
    }

    public static void openSession(ResourceLocation bookId) {
        PacketDistributor.sendToServer(new OpenSessionPayload(bookId.toString()));
    }

    public record OpenLivePayload(String bookId) implements CustomPacketPayload {
        public static final Type<OpenLivePayload> TYPE = AuthoringNetwork.type("editor_open_live");
        public static final StreamCodec<ByteBuf, OpenLivePayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(256), OpenLivePayload::bookId, OpenLivePayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void openLiveSession(ResourceLocation bookId) {
        PacketDistributor.sendToServer(new OpenLivePayload(bookId.toString()));
    }

    public static void openCurrentSession(ResourceLocation bookId, String activeRevision,
                                          String draftRevision, boolean replaceDraft) {
        PacketDistributor.sendToServer(new OpenCurrentSessionPayload(bookId.toString(), activeRevision,
                draftRevision == null ? "" : draftRevision, replaceDraft));
    }

    public static void renewSession(UUID sessionId, String draftRevision) {
        PacketDistributor.sendToServer(new RenewSessionPayload(sessionId.toString(), draftRevision));
    }

    public static void closeSession(UUID sessionId, String draftRevision) {
        PacketDistributor.sendToServer(new CloseSessionPayload(sessionId.toString(), draftRevision));
    }

    public static void recoverSession(UUID sessionId, ResourceLocation bookId, String action,
                                      ResourceLocation targetBookId) {
        RecoveryWire wire = new RecoveryWire(sessionId.toString(), bookId.toString(), action,
                targetBookId == null ? "" : targetBookId.toString());
        PacketDistributor.sendToServer(new RecoverSessionPayload(GSON.toJson(wire)));
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
        updateQuest(sessionId, bookId, draftRevision, questId, replacementQuestId, title, subtitle,
                description, iconKind, iconValue, preserveIcon, null, null, null, null, null, null);
    }

    /** Optional coordinates let the full property form update exact values without changing quick text edits. */
    public static void updateQuest(UUID sessionId, ResourceLocation bookId, String draftRevision,
                                   ResourceLocation questId, ResourceLocation replacementQuestId,
                                   String title, String subtitle, String description, String iconKind, String iconValue,
                                   boolean preserveIcon, Double x, Double y) {
        updateQuest(sessionId, bookId, draftRevision, questId, replacementQuestId, title, subtitle, description,
                iconKind, iconValue, preserveIcon, x, y, null, null, null, null);
    }

    public static void updateQuest(UUID sessionId, ResourceLocation bookId, String draftRevision,
                                   ResourceLocation questId, ResourceLocation replacementQuestId,
                                   String title, String subtitle, String description, String iconKind, String iconValue,
                                   boolean preserveIcon, Double x, Double y, String shape, Double size,
                                   Double iconScale, Double minWidth) {
        updateQuest(sessionId, bookId, draftRevision, questId, replacementQuestId, title, subtitle, description,
                iconKind, iconValue, preserveIcon, x, y, shape, size, iconScale, minWidth, Map.of());
    }

    public static void updateQuest(UUID sessionId, ResourceLocation bookId, String draftRevision,
                                   ResourceLocation questId, ResourceLocation replacementQuestId,
                                   String title, String subtitle, String description, String iconKind, String iconValue,
                                   boolean preserveIcon, Double x, Double y, String shape, Double size,
                                   Double iconScale, Double minWidth, Map<String, String> behavior) {
        QuestUpdateWire wire = new QuestUpdateWire(sessionId.toString(), bookId.toString(), draftRevision,
                questId.toString(), replacementQuestId.toString(), title, subtitle, description,
                iconKind, iconValue, preserveIcon, x, y, shape, size, iconScale, minWidth,
                behavior == null ? Map.of() : Map.copyOf(behavior));
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

    public static void reviewPublish(UUID sessionId, ResourceLocation bookId, String draftRevision) {
        mutate(new EditorMutationWire(sessionId.toString(), bookId.toString(), draftRevision,
                "REVIEW", "", "", "", "", 0, 0.0D, 0.0D, List.of(), Map.of()));
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(BRNQuest.MOD_ID, path));
    }
}
