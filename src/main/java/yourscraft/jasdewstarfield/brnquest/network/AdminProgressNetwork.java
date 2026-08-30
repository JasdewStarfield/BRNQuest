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
import yourscraft.jasdewstarfield.brnquest.api.OperationResult;
import yourscraft.jasdewstarfield.brnquest.progress.AdminProgressService;

/** Bounded management messages carry intents or opaque server-issued confirmation tickets, never state writes. */
public final class AdminProgressNetwork {
    private static final Gson GSON = new Gson();
    private AdminProgressNetwork() {}
    public record Request(String requestId, String mode, AdminProgressService.Intent intent, String token, String filter) {}
    public record Response(String requestId, AdminProgressService.Reply reply) {}

    public record RequestPayload(String json) implements CustomPacketPayload {
        public static final Type<RequestPayload> TYPE = new Type<>(ResourceLocation.parse("brnquest:admin_progress_request"));
        public static final StreamCodec<ByteBuf, RequestPayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(16_384), RequestPayload::json, RequestPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record ResponsePayload(String json) implements CustomPacketPayload {
        public static final Type<ResponsePayload> TYPE = new Type<>(ResourceLocation.parse("brnquest:admin_progress_response"));
        public static final StreamCodec<ByteBuf, ResponsePayload> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(131_072), ResponsePayload::json, ResponsePayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    static void register(PayloadRegistrar registrar) {
        registrar.playToServer(RequestPayload.TYPE, RequestPayload.CODEC, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer actor)) return;
            Request request = null;
            AdminProgressService.Reply reply;
            try {
                request = GSON.fromJson(payload.json(), Request.class);
                if (request == null || request.requestId() == null || request.requestId().length() > 64
                        || request.mode() == null) throw new IllegalArgumentException("Missing request identity");
                var service = AdminProgressService.get();
                var source = actor.createCommandSourceStack();
                reply = switch (request.mode()) {
                    case "CATALOG" -> service.catalog(source, request.filter());
                    case "INSPECT" -> service.inspect(source, request.intent(), false);
                    case "PREVIEW" -> service.inspect(source, request.intent(), true);
                    case "COMMIT" -> service.confirm(source, request.token());
                    default -> throw new IllegalArgumentException("Unknown management action");
                };
            } catch (com.google.gson.JsonParseException | IllegalArgumentException exception) {
                reply = new AdminProgressService.Reply(OperationResult.invalid(
                        "INVALID_REQUEST", "Malformed administrator request"), java.util.List.of(), null, "");
            }
            String id = request == null || request.requestId() == null || request.requestId().length() > 64
                    ? "" : request.requestId();
            BrnQuestNetwork.send(actor, new ResponsePayload(GSON.toJson(new Response(id, reply))));
        });
        if (FMLEnvironment.dist == Dist.CLIENT) {
            registrar.playToClient(ResponsePayload.TYPE, ResponsePayload.CODEC, (payload, context) -> ClientDelegate.receive(payload));
        } else registrar.playToClient(ResponsePayload.TYPE, ResponsePayload.CODEC, (payload, context) -> {});
    }

    public static void send(Request request) { PacketDistributor.sendToServer(new RequestPayload(GSON.toJson(request))); }

    /** Isolates client classes from dedicated-server payload registration. */
    private static final class ClientDelegate {
        static void receive(ResponsePayload payload) {
            Response response = GSON.fromJson(payload.json(), Response.class);
            if (net.minecraft.client.Minecraft.getInstance().screen
                    instanceof yourscraft.jasdewstarfield.brnquest.client.ui.AdminProgressScreen screen) {
                screen.receive(response);
            }
        }
    }
}
