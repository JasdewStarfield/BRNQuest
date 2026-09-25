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
            dispatch(player, "OPEN", AuthoringRequestDecoder.live(payload.bookId()),
                        request -> new AuthoringSessionHandler(player).openLive(request));
        });
        registrar.playToServer(RequestCatalogPayload.TYPE, RequestCatalogPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) new AuthoringSessionHandler(player).sendCatalog();
        });
        registrar.playToServer(RequestDraftVersionsPayload.TYPE, RequestDraftVersionsPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                dispatch(player, "VERSIONS", AuthoringRequestDecoder.open(payload.bookId(), ""),
                        request -> new AuthoringSessionHandler(player).sendDraftVersions(request.bookId()));
            }
        });
        registrar.playToServer(VersionSavedDraftPayload.TYPE, VersionSavedDraftPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                dispatch(player, "OPEN", AuthoringRequestDecoder.version(payload.bookId(), payload.expectedDraftRevision()),
                        request -> new AuthoringSessionHandler(player).versionSavedDraft(request));
            }
        });
        registrar.playToServer(RestoreDraftVersionPayload.TYPE, RestoreDraftVersionPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                dispatch(player, "OPEN", AuthoringRequestDecoder.restoreDraft(payload),
                        request -> new AuthoringSessionHandler(player).restoreDraftVersion(request));
            }
        });
        registrar.playToServer(OpenSessionPayload.TYPE, OpenSessionPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                dispatch(player, "OPEN", AuthoringRequestDecoder.open(payload.bookId(), payload.expectedDraftRevision()),
                        request -> new AuthoringSessionHandler(player).open(request));
            }
        });
        registrar.playToServer(OpenCurrentSessionPayload.TYPE, OpenCurrentSessionPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                dispatch(player, "OPEN", AuthoringRequestDecoder.current(payload),
                        request -> new AuthoringSessionHandler(player).openCurrent(request));
            }
        });
        registrar.playToServer(RenewSessionPayload.TYPE, RenewSessionPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                dispatch(player, "RENEW", AuthoringRequestDecoder.lease(payload.sessionId(), payload.draftRevision()),
                        request -> new AuthoringSessionHandler(player).renew(request));
            }
        });
        registrar.playToServer(CloseSessionPayload.TYPE, CloseSessionPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                dispatch(player, "CLOSE", AuthoringRequestDecoder.lease(payload.sessionId(), payload.draftRevision()),
                        request -> new AuthoringSessionHandler(player).close(request));
            }
        });
        registrar.playToServer(RecoverSessionPayload.TYPE, RecoverSessionPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                dispatch(player, "RECOVER", AuthoringRequestDecoder.recovery(payload.json()),
                        request -> new AuthoringSessionHandler(player).recover(request));
            }
        });
        registrar.playToServer(SaveSessionPayload.TYPE, SaveSessionPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                dispatch(player, "SAVE", AuthoringRequestDecoder.publication(payload.sessionId(), payload.bookId(), payload.draftRevision(), false),
                        request -> new AuthoringPublicationHandler(player).save(request));
            }
        });
        registrar.playToServer(PublishApplyPayload.TYPE, PublishApplyPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                dispatch(player, "PUBLISH", AuthoringRequestDecoder.reviewedPublication(payload),
                        request -> new AuthoringPublicationHandler(player).publishAndApply(request));
            }
        });
        registrar.playToServer(UpdateQuestPayload.TYPE, UpdateQuestPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                dispatch(player, "UPDATE", AuthoringRequestDecoder.quest(payload.json()),
                        request -> new AuthoringQuestUpdateHandler(player).update(request));
            }
        });
        registrar.playToServer(EditorMutationPayload.TYPE, EditorMutationPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                dispatch(player, "MUTATE", AuthoringRequestDecoder.mutation(payload.json()),
                        request -> new AuthoringMutationHandler(player).mutate(request));
            }
        });
        registerClient(registrar, CatalogPayload.TYPE, CatalogPayload.CODEC, FMLEnvironment.dist == Dist.CLIENT ? ClientDelegate::catalog : (payload, context) -> {});
        registerClient(registrar, DraftVersionsPayload.TYPE, DraftVersionsPayload.CODEC,
                FMLEnvironment.dist == Dist.CLIENT ? ClientDelegate::draftVersions : (payload, context) -> {});
        registerClient(registrar, SessionPayload.TYPE, SessionPayload.CODEC, FMLEnvironment.dist == Dist.CLIENT ? ClientDelegate::session : (payload, context) -> {});
        registerClient(registrar, DraftChunkPayload.TYPE, DraftChunkPayload.CODEC, FMLEnvironment.dist == Dist.CLIENT ? ClientDelegate::draftChunk : (payload, context) -> {});
    }

    /** Route only decoded requests; the response boundary owns all rejection formatting. */
    private static <T> void dispatch(ServerPlayer player, String action, AuthoringRequestDecoder.Result<T> result,
                                     java.util.function.Consumer<T> handler) {
        if (result.success()) handler.accept(result.value());
        else AuthoringResponseSender.forPlayer(player).sendDecodeFailure(action, result.failure());
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

        static void draftVersions(DraftVersionsPayload payload,
                                  net.neoforged.neoforge.network.handling.IPayloadContext context) {
            ClientPayloadHandler.editorDraftVersions(payload);
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
