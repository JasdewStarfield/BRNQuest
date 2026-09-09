package yourscraft.jasdewstarfield.brnquest.network;

import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import yourscraft.jasdewstarfield.brnquest.editor.ServerFieldSources;

/** Bounded, read-only editor queries; the normal author mutation protocol still owns every write. */
public final class ServerFieldNetwork {
    private static final Gson JSON = new Gson();
    private ServerFieldNetwork() {}
    public record Query(String id, String source, String filter, String selected, int offset, java.util.Map<String,String> context) {
        public Query(String id, String source, String filter, String selected, int offset) { this(id,source,filter,selected,offset,java.util.Map.of()); }
        public Query(String id, String source, String filter, String selected) { this(id, source, filter, selected, 0); }
    }
    public record Reply(String id, ServerFieldSources.Result result) {}
    public record Request(String json) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(ResourceLocation.parse("brnquest:field_query"));
        public static final StreamCodec<ByteBuf, Request> CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(65536),Request::json,Request::new);
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Response(String json) implements CustomPacketPayload {
        public static final Type<Response> TYPE = new Type<>(ResourceLocation.parse("brnquest:field_result"));
        public static final StreamCodec<ByteBuf, Response> CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(131072),Response::json,Response::new);
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    /** Reject oversized or null field data before a provider sees it. */
    static boolean validContext(java.util.Map<String,String> fields) {
        return fields.size() <= 64 && fields.entrySet().stream().allMatch(e -> e.getKey() != null && e.getKey().length() <= 128
                && e.getValue() != null && e.getValue().length() <= 256);
    }
    static void register(PayloadRegistrar registrar) {
        registrar.playToServer(Request.TYPE, Request.CODEC, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            try {
                var query = JSON.fromJson(payload.json(), Query.class);
                if (query == null || query.offset() < 0 || query.id() == null || query.id().length() > 64 || query.source() == null || query.source().length() > 256
                        || query.filter() == null || query.filter().length() > 128 || query.selected() == null || query.selected().length() > 256) return;
                // Bound dependent-field input independently of the transport limit.
                var fields = query.context() == null ? java.util.Map.<String,String>of() : query.context();
                if (!validContext(fields)) return;
                var source = ResourceLocation.tryParse(query.source());
                if (source == null) return;
                var result = ServerFieldSources.query(player, source, query.filter(), query.selected(), query.offset(), fields);
                BrnQuestNetwork.send(player, new Response(JSON.toJson(new Reply(query.id(),result))));
            } catch (com.google.gson.JsonParseException | IllegalArgumentException ignored) { /* Malformed read requests never reach a source or mutation. */ }
        });
        registrar.playToClient(Response.TYPE, Response.CODEC, (payload, context) -> {
            if (FMLEnvironment.dist == Dist.CLIENT) ClientDelegate.receive(payload);
        });
    }
    public static void send(Query query) { PacketDistributor.sendToServer(new Request(JSON.toJson(query))); }
    private static class ClientDelegate {
        static void receive(Response payload) {
            var reply = JSON.fromJson(payload.json(), Reply.class);
            if (net.minecraft.client.Minecraft.getInstance().screen instanceof yourscraft.jasdewstarfield.brnquest.client.ui.ServerFieldScreen screen) screen.receive(reply);
            else if (net.minecraft.client.Minecraft.getInstance().screen instanceof yourscraft.jasdewstarfield.brnquest.client.ui.QuestScreen screen) screen.receiveServerField(reply);
        }
    }
}
