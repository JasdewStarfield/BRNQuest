package yourscraft.jasdewstarfield.brnquest.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import yourscraft.jasdewstarfield.brnquest.BRNQuest;
import yourscraft.jasdewstarfield.brnquest.BrnQuestConstants;

/** Read-only gaze telemetry is distinct from persistent completion and cannot be submitted by clients. */
@EventBusSubscriber(modid=BRNQuest.MOD_ID)
public final class ObservationNetwork {
    private ObservationNetwork() {}
    public record Payload(String book,String task,int ticks) implements CustomPacketPayload {
        public static final Type<Payload> TYPE=new Type<>(ResourceLocation.parse("brnquest:observation_progress"));
        public static final StreamCodec<ByteBuf,Payload> CODEC=StreamCodec.composite(ByteBufCodecs.STRING_UTF8,Payload::book,
                ByteBufCodecs.STRING_UTF8,Payload::task,ByteBufCodecs.VAR_INT,Payload::ticks,Payload::new);
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(BrnQuestConstants.NETWORK_PROTOCOL).playToClient(Payload.TYPE,Payload.CODEC,(payload,context)->ClientDelegate.receive(payload));
    }
    public static void send(ServerPlayer player,ResourceLocation book,ResourceLocation task,int ticks) {
        if(player.connection!=null && NetworkRegistry.hasChannel(player.connection,Payload.TYPE.id()))
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,new Payload(book.toString(),task.toString(),ticks));
    }
    private static class ClientDelegate {
        static void receive(Payload payload) { yourscraft.jasdewstarfield.brnquest.client.ui.ObservationDisplay.receive(payload); }
    }
}
