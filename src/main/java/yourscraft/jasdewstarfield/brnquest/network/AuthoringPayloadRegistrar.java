package yourscraft.jasdewstarfield.brnquest.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import yourscraft.jasdewstarfield.brnquest.client.ClientPayloadHandler;

import static yourscraft.jasdewstarfield.brnquest.network.AuthoringNetwork.*;

/** Protocol wiring only. NeoForge's registrar retains its default main-thread dispatch. */
final class AuthoringPayloadRegistrar {
    private AuthoringPayloadRegistrar() {}

    static void register(PayloadRegistrar registrar) {
        registrar.playToServer(OpenLivePayload.TYPE, OpenLivePayload.CODEC, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            AuthoringNetwork.openLive(player, payload.bookId());
        });
        registrar.playToServer(RequestCatalogPayload.TYPE, RequestCatalogPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) AuthoringNetwork.sendCatalog(player);
        });
        registrar.playToServer(OpenSessionPayload.TYPE, OpenSessionPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) AuthoringNetwork.open(player, payload.bookId());
        });
        registrar.playToServer(OpenCurrentSessionPayload.TYPE, OpenCurrentSessionPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) AuthoringNetwork.openCurrent(player, payload);
        });
        registrar.playToServer(RenewSessionPayload.TYPE, RenewSessionPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) AuthoringNetwork.renew(player, payload.sessionId(), payload.draftRevision());
        });
        registrar.playToServer(CloseSessionPayload.TYPE, CloseSessionPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) AuthoringNetwork.close(player, payload.sessionId(), payload.draftRevision());
        });
        registrar.playToServer(RecoverSessionPayload.TYPE, RecoverSessionPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) AuthoringNetwork.recover(player, payload.json());
        });
        registrar.playToServer(SaveSessionPayload.TYPE, SaveSessionPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                AuthoringNetwork.save(player, payload.sessionId(), payload.bookId(), payload.draftRevision());
            }
        });
        registrar.playToServer(PublishApplyPayload.TYPE, PublishApplyPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                AuthoringNetwork.publishAndApply(player, payload.sessionId(), payload.bookId(), payload.draftRevision());
            }
        });
        registrar.playToServer(UpdateQuestPayload.TYPE, UpdateQuestPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) AuthoringNetwork.updateQuest(player, payload.json());
        });
        registrar.playToServer(EditorMutationPayload.TYPE, EditorMutationPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) AuthoringNetwork.mutate(player, payload.json());
        });
        registerClient(registrar, CatalogPayload.TYPE, CatalogPayload.CODEC, FMLEnvironment.dist == Dist.CLIENT ? ClientDelegate::catalog : (payload, context) -> {});
        registerClient(registrar, SessionPayload.TYPE, SessionPayload.CODEC, FMLEnvironment.dist == Dist.CLIENT ? ClientDelegate::session : (payload, context) -> {});
        registerClient(registrar, DraftChunkPayload.TYPE, DraftChunkPayload.CODEC, FMLEnvironment.dist == Dist.CLIENT ? ClientDelegate::draftChunk : (payload, context) -> {});
    }

    private static <T extends CustomPacketPayload> void registerClient(PayloadRegistrar registrar,
                                                                        CustomPacketPayload.Type<T> type,
                                                                        StreamCodec<? super ByteBuf, T> codec,
                                                                        net.neoforged.neoforge.network.handling.IPayloadHandler<T> handler) {
        registrar.playToClient(type, codec, handler);
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
