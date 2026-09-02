package yourscraft.jasdewstarfield.brnquest.network;

import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.jetbrains.annotations.NotNull;
import yourscraft.jasdewstarfield.brnquest.BRNQuest;
import yourscraft.jasdewstarfield.brnquest.BrnQuestConstants;
import yourscraft.jasdewstarfield.brnquest.client.ClientPayloadHandler;
import yourscraft.jasdewstarfield.brnquest.api.BrnQuestApi;
import yourscraft.jasdewstarfield.brnquest.api.OperationContext;
import yourscraft.jasdewstarfield.brnquest.data.NativeBookJson;
import yourscraft.jasdewstarfield.brnquest.progress.ProgressEngine;
import yourscraft.jasdewstarfield.brnquest.progress.PlayerProgress;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;
import yourscraft.jasdewstarfield.brnquest.task.TaskSubmissionSelection;

import java.util.ArrayList;
import java.util.List;

/** Protocol 2 payloads; client messages contain intent and are revalidated on the server. */
@EventBusSubscriber(modid = BRNQuest.MOD_ID)
public final class BrnQuestNetwork {
    private static final Gson GSON = new Gson();
    static final int BOOK_CHUNK_CHARACTERS = 30_000;
    private BrnQuestNetwork() {}

    public record HelloPayload(int dataSchema, String revision) implements CustomPacketPayload {
        public static final Type<HelloPayload> TYPE = payloadType("hello");
        public static final StreamCodec<ByteBuf, HelloPayload> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, HelloPayload::dataSchema, ByteBufCodecs.STRING_UTF8, HelloPayload::revision, HelloPayload::new);
        public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record BookManifestPayload(String bookId, String revision, int chunks, int decodedBytes) implements CustomPacketPayload {
        public static final Type<BookManifestPayload> TYPE = payloadType("book_manifest");
        public static final StreamCodec<ByteBuf, BookManifestPayload> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, BookManifestPayload::bookId, ByteBufCodecs.STRING_UTF8, BookManifestPayload::revision, ByteBufCodecs.VAR_INT, BookManifestPayload::chunks, ByteBufCodecs.VAR_INT, BookManifestPayload::decodedBytes, BookManifestPayload::new);
        public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record BookChunkPayload(String revision, int index, String data) implements CustomPacketPayload {
        public static final Type<BookChunkPayload> TYPE = payloadType("book_chunk");
        public static final StreamCodec<ByteBuf, BookChunkPayload> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, BookChunkPayload::revision, ByteBufCodecs.VAR_INT, BookChunkPayload::index, ByteBufCodecs.STRING_UTF8, BookChunkPayload::data, BookChunkPayload::new);
        public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record ProgressSnapshotPayload(String json, boolean toast) implements CustomPacketPayload {
        public static final Type<ProgressSnapshotPayload> TYPE = payloadType("progress_snapshot");
        public static final StreamCodec<ByteBuf, ProgressSnapshotPayload> CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(BrnQuestConstants.MAX_PROGRESS_BYTES), ProgressSnapshotPayload::json, ByteBufCodecs.BOOL, ProgressSnapshotPayload::toast, ProgressSnapshotPayload::new);
        public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record ProgressDeltaPayload(String json) implements CustomPacketPayload {
        public static final Type<ProgressDeltaPayload> TYPE = payloadType("progress_delta");
        public static final StreamCodec<ByteBuf, ProgressDeltaPayload> CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(BrnQuestConstants.MAX_PROGRESS_BYTES), ProgressDeltaPayload::json, ProgressDeltaPayload::new);
        public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record QuestToastPayload(String message) implements CustomPacketPayload {
        public static final Type<QuestToastPayload> TYPE = payloadType("quest_toast");
        public static final StreamCodec<ByteBuf, QuestToastPayload> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, QuestToastPayload::message, QuestToastPayload::new);
        public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record OpenScreenPayload(String questId) implements CustomPacketPayload {
        public static final Type<OpenScreenPayload> TYPE = payloadType("open_screen");
        public static final StreamCodec<ByteBuf, OpenScreenPayload> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, OpenScreenPayload::questId, OpenScreenPayload::new);
        public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record RequestBookPayload(String knownRevision) implements CustomPacketPayload {
        public static final Type<RequestBookPayload> TYPE = payloadType("request_book");
        public static final StreamCodec<ByteBuf, RequestBookPayload> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, RequestBookPayload::knownRevision, RequestBookPayload::new);
        public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record RequestOpenPayload(String knownRevision) implements CustomPacketPayload {
        public static final Type<RequestOpenPayload> TYPE = payloadType("request_open");
        public static final StreamCodec<ByteBuf, RequestOpenPayload> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, RequestOpenPayload::knownRevision, RequestOpenPayload::new);
        public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record CompleteCheckmarkPayload(String revision, String questId) implements CustomPacketPayload {
        public static final Type<CompleteCheckmarkPayload> TYPE = payloadType("complete_checkmark");
        public static final StreamCodec<ByteBuf, CompleteCheckmarkPayload> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, CompleteCheckmarkPayload::revision, ByteBufCodecs.STRING_UTF8, CompleteCheckmarkPayload::questId, CompleteCheckmarkPayload::new);
        public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record CompleteTaskPayload(String revision, String questId, String taskId,
                                      String selectedSlots) implements CustomPacketPayload {
        public static final Type<CompleteTaskPayload> TYPE = payloadType("complete_task");
        public static final StreamCodec<ByteBuf, CompleteTaskPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, CompleteTaskPayload::revision,
                ByteBufCodecs.STRING_UTF8, CompleteTaskPayload::questId,
                ByteBufCodecs.STRING_UTF8, CompleteTaskPayload::taskId,
                ByteBufCodecs.stringUtf8(256), CompleteTaskPayload::selectedSlots,
                CompleteTaskPayload::new);
        public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record ToggleTrackedPayload(String revision, String questId) implements CustomPacketPayload {
        public static final Type<ToggleTrackedPayload> TYPE = payloadType("toggle_tracked");
        public static final StreamCodec<ByteBuf, ToggleTrackedPayload> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, ToggleTrackedPayload::revision, ByteBufCodecs.STRING_UTF8, ToggleTrackedPayload::questId, ToggleTrackedPayload::new);
        public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record ClaimRewardPayload(String revision, String rewardId) implements CustomPacketPayload {
        public static final Type<ClaimRewardPayload> TYPE = payloadType("claim_reward");
        public static final StreamCodec<ByteBuf, ClaimRewardPayload> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, ClaimRewardPayload::revision, ByteBufCodecs.STRING_UTF8, ClaimRewardPayload::rewardId, ClaimRewardPayload::new);
        public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record SelectQuestPayload(String revision, String questId) implements CustomPacketPayload {
        public static final Type<SelectQuestPayload> TYPE = payloadType("select_quest");
        public static final StreamCodec<ByteBuf, SelectQuestPayload> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, SelectQuestPayload::revision, ByteBufCodecs.STRING_UTF8, SelectQuestPayload::questId, SelectQuestPayload::new);
        public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(BrnQuestConstants.NETWORK_PROTOCOL);
        registrar.playToServer(RequestBookPayload.TYPE, RequestBookPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                // Definition requests also serve as bounded recovery synchronization for an open client.
                ProgressEngine.get().reconcile(player);
                syncAll(player, payload.knownRevision().equals(currentRevision()));
            }
        });
        registrar.playToServer(RequestOpenPayload.TYPE, RequestOpenPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                syncAll(player, payload.knownRevision().equals(currentRevision()));
                openScreen(player, "");
            }
        });
        registrar.playToServer(CompleteCheckmarkPayload.TYPE, CompleteCheckmarkPayload.CODEC, (payload, context) -> {
            ResourceLocation id = ResourceLocation.tryParse(payload.questId());
            if (context.player() instanceof ServerPlayer player && id != null && payload.revision().equals(currentRevision())) {
                BrnQuestApi.submitQuestCompletionResult(OperationContext.self(player), player, id.toString(), true);
            }
        });
        registrar.playToServer(CompleteTaskPayload.TYPE, CompleteTaskPayload.CODEC, (payload, context) -> {
            ResourceLocation questId = ResourceLocation.tryParse(payload.questId());
            ResourceLocation taskId = ResourceLocation.tryParse(payload.taskId());
            TaskSubmissionSelection selection = parseSelection(payload.selectedSlots());
            if (context.player() instanceof ServerPlayer player && questId != null && taskId != null
                    && payload.revision().equals(currentRevision())) {
                if (selection == null) {
                    // A malformed bounded selection must not leave the client stuck in its optimistic pending state.
                    syncProgress(player, true);
                    return;
                }
                BrnQuestApi.completeTaskResult(OperationContext.self(player), player,
                        questId.toString(), taskId.toString(), selection);
            }
        });
        registrar.playToServer(ToggleTrackedPayload.TYPE, ToggleTrackedPayload.CODEC, (payload, context) -> {
            ResourceLocation id = ResourceLocation.tryParse(payload.questId());
            if (context.player() instanceof ServerPlayer player && id != null && payload.revision().equals(currentRevision())) {
                BrnQuestApi.toggleTrackedResult(OperationContext.self(player), player, id.toString());
            }
        });
        registrar.playToServer(ClaimRewardPayload.TYPE, ClaimRewardPayload.CODEC, (payload, context) -> {
            ResourceLocation id = ResourceLocation.tryParse(payload.rewardId());
            if (context.player() instanceof ServerPlayer player && id != null && payload.revision().equals(currentRevision())) {
                BrnQuestApi.claimRewardResult(OperationContext.self(player), player, id.toString());
            }
        });
        registrar.playToServer(SelectQuestPayload.TYPE, SelectQuestPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer && payload.revision().equals(currentRevision()) && ResourceLocation.tryParse(payload.questId()) != null) {
                // Selection has no authority effect; accepting the intent keeps protocol evolution explicit.
            }
        });
        registerClient(registrar, HelloPayload.TYPE, HelloPayload.CODEC, ClientDelegate::hello);
        registerClient(registrar, BookManifestPayload.TYPE, BookManifestPayload.CODEC, ClientDelegate::manifest);
        registerClient(registrar, BookChunkPayload.TYPE, BookChunkPayload.CODEC, ClientDelegate::chunk);
        registerClient(registrar, ProgressSnapshotPayload.TYPE, ProgressSnapshotPayload.CODEC, ClientDelegate::progress);
        registerClient(registrar, ProgressDeltaPayload.TYPE, ProgressDeltaPayload.CODEC, ClientDelegate::delta);
        registerClient(registrar, QuestToastPayload.TYPE, QuestToastPayload.CODEC, ClientDelegate::toast);
        registerClient(registrar, OpenScreenPayload.TYPE, OpenScreenPayload.CODEC, ClientDelegate::open);
        AuthoringNetwork.register(registrar);
        AdminProgressNetwork.register(registrar);
    }

    public static void syncAll(ServerPlayer player, boolean revisionMatches) {
        var snapshot = QuestBookManager.get().active().orElse(null);
        if (snapshot == null) return;
        send(player, new HelloPayload(BrnQuestConstants.DATA_SCHEMA, snapshot.revision()));
        if (!revisionMatches) sendBook(player);
        syncProgress(player, false);
    }

    private static void sendBook(ServerPlayer player) {
        var snapshot = QuestBookManager.get().active().orElseThrow();
        String json = NativeBookJson.encode(snapshot.book());
        byte[] bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length > BrnQuestConstants.MAX_BOOK_BYTES || snapshot.book().quests().size() > BrnQuestConstants.MAX_QUESTS) return;
        // Minecraft's generic UTF-8 string codec caps a single String at 32,767
        // characters, independently of BRNQuest's 256 KiB logical chunk limit.
        // Staying below both limits also leaves room for the revision and index.
        List<String> chunks = split(json, BOOK_CHUNK_CHARACTERS);
        send(player, new BookManifestPayload(snapshot.book().id().toString(), snapshot.revision(), chunks.size(), bytes.length));
        for (int i = 0; i < chunks.size(); i++) send(player, new BookChunkPayload(snapshot.revision(), i, chunks.get(i)));
    }

    public static void syncProgress(ServerPlayer player, boolean changed) {
        PlayerProgress progress = ProgressEngine.get().progress(player);
        String json = GSON.toJson(new ProgressWire(progress.questsView(), progress.taskProgressView(),
                progress.claimedRewardsView(), ProgressEngine.get().visibleQuestIds(player),
                progress.completionCyclesView(), progress.nextAvailableTimesView(), progress.revision()));
        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > BrnQuestConstants.MAX_PROGRESS_BYTES) return;
        if (changed) {
            send(player, new ProgressDeltaPayload(json));
        } else {
            send(player, new ProgressSnapshotPayload(json, false));
        }
    }

    public static void openScreen(ServerPlayer player, String questId) { send(player, new OpenScreenPayload(questId)); }
    public static void requestBook(String revision) { PacketDistributor.sendToServer(new RequestBookPayload(revision)); }
    public static void requestOpen(String knownRevision) { PacketDistributor.sendToServer(new RequestOpenPayload(knownRevision)); }
    public static void completeCheckmark(String revision, String questId) { PacketDistributor.sendToServer(new CompleteCheckmarkPayload(revision, questId)); }
    public static void completeTask(String revision, String questId, String taskId) {
        completeTask(revision, questId, taskId, List.of());
    }
    public static void completeTask(String revision, String questId, String taskId, List<Integer> selectedSlots) {
        String encoded = selectedSlots.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        PacketDistributor.sendToServer(new CompleteTaskPayload(revision, questId, taskId, encoded));
    }
    public static void toggleTracked(String revision, String questId) { PacketDistributor.sendToServer(new ToggleTrackedPayload(revision, questId)); }
    public static void claimReward(String revision, String rewardId) { PacketDistributor.sendToServer(new ClaimRewardPayload(revision, rewardId)); }
    public static void selectQuest(String revision, String questId) { PacketDistributor.sendToServer(new SelectQuestPayload(revision, questId)); }

    private static String currentRevision() { return QuestBookManager.get().active().map(s -> s.revision()).orElse(""); }
    private static TaskSubmissionSelection parseSelection(String encoded) {
        try {
            if (encoded == null || encoded.isBlank()) return TaskSubmissionSelection.AUTOMATIC;
            List<Integer> indices = java.util.Arrays.stream(encoded.split(","))
                    .map(Integer::parseInt).toList();
            return new TaskSubmissionSelection(indices);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
    static void send(ServerPlayer player, CustomPacketPayload payload) {
        // Mock players and clients without the negotiated channel must not make
        // otherwise server-only progress operations fail.
        if (player.connection != null && NetworkRegistry.hasChannel(player.connection, payload.type().id())) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
    static List<String> split(String value, int characters) { List<String> result = new ArrayList<>(); for (int i = 0; i < value.length(); i += characters) result.add(value.substring(i, Math.min(value.length(), i + characters))); return result; }
    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) { return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(BRNQuest.MOD_ID, path)); }
    private static <T extends CustomPacketPayload> void registerClient(PayloadRegistrar registrar, CustomPacketPayload.Type<T> type, StreamCodec<? super ByteBuf, T> codec, net.neoforged.neoforge.network.handling.IPayloadHandler<T> handler) {
        if (FMLEnvironment.dist == Dist.CLIENT) registrar.playToClient(type, codec, handler); else registrar.playToClient(type, codec, (p, c) -> {});
    }
    public record ProgressWire(java.util.Map<String, yourscraft.jasdewstarfield.brnquest.progress.QuestStatus> quests,
                               java.util.Map<String, Long> tasks, java.util.Set<String> claimed,
                               java.util.Set<String> visible, java.util.Map<String, Integer> cycles,
                               java.util.Map<String, Long> nextAvailable, String revision) {}
    private static final class ClientDelegate {
        static void hello(HelloPayload p, net.neoforged.neoforge.network.handling.IPayloadContext c) { ClientPayloadHandler.hello(p); }
        static void manifest(BookManifestPayload p, net.neoforged.neoforge.network.handling.IPayloadContext c) { ClientPayloadHandler.manifest(p); }
        static void chunk(BookChunkPayload p, net.neoforged.neoforge.network.handling.IPayloadContext c) { ClientPayloadHandler.chunk(p); }
        static void progress(ProgressSnapshotPayload p, net.neoforged.neoforge.network.handling.IPayloadContext c) { ClientPayloadHandler.progress(p); }
        static void delta(ProgressDeltaPayload p, net.neoforged.neoforge.network.handling.IPayloadContext c) { ClientPayloadHandler.delta(p); }
        static void toast(QuestToastPayload p, net.neoforged.neoforge.network.handling.IPayloadContext c) { ClientPayloadHandler.toast(p); }
        static void open(OpenScreenPayload p, net.neoforged.neoforge.network.handling.IPayloadContext c) { ClientPayloadHandler.open(p); }
    }
}
