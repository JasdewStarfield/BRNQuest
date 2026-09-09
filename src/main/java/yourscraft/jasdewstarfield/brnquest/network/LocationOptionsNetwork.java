package yourscraft.jasdewstarfield.brnquest.network;

import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import yourscraft.jasdewstarfield.brnquest.BRNQuest;
import yourscraft.jasdewstarfield.brnquest.BrnQuestConstants;
import yourscraft.jasdewstarfield.brnquest.task.location.LocationTargets;
import java.util.*;

/** Ordinary players receive read-only resolved location selectors without access to author queries. */
@EventBusSubscriber(modid=BRNQuest.MOD_ID)
public final class LocationOptionsNetwork {
    private static final Gson JSON=new Gson();
    private LocationOptionsNetwork() {}
    public record Page(String revision,String selector,int offset,int total,List<String> members,boolean reset) {}
    public record Payload(String json) implements CustomPacketPayload {
        public static final Type<Payload> TYPE=new Type<>(ResourceLocation.parse("brnquest:location_options"));
        public static final StreamCodec<ByteBuf,Payload> CODEC=StreamCodec.composite(ByteBufCodecs.stringUtf8(131072),Payload::json,Payload::new);
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(BrnQuestConstants.NETWORK_PROTOCOL).playToClient(Payload.TYPE,Payload.CODEC,(payload,context)->ClientDelegate.receive(payload));
    }
    private static class ClientDelegate {
        static void receive(Payload payload) { yourscraft.jasdewstarfield.brnquest.client.ui.LocationOptionsCache.receive(JSON.fromJson(payload.json(),Page.class)); }
    }
    @SubscribeEvent public static void sync(OnDatapackSyncEvent event) {
        var players=(event.getPlayer()==null ? event.getPlayerList().getPlayers() : List.of(event.getPlayer())).stream()
                .filter(player->player.connection!=null && net.neoforged.neoforge.network.registration.NetworkRegistry.hasChannel(player.connection,Payload.TYPE.id())).toList();
        if(players.isEmpty()) return;
        for(var page:snapshot(players.getFirst())) for(var player:players)
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,new Payload(JSON.toJson(page)));
    }
    /** The same registry snapshot is shared by all recipients; reload first clears removed selectors. */
    public static List<Page> snapshot(ServerPlayer player) {
        String revision=UUID.randomUUID().toString(); var pages=new ArrayList<Page>();
        pages.add(new Page(revision,"",0,0,List.of(),true));
        for(String kind:List.of("dimension","biome","structure")) {
            var selectors=new TreeSet<String>();
            if(kind.equals("dimension")) LocationTargets.groups(player.server).keySet().forEach(id->selectors.add("#"+id));
            else player.registryAccess().registryOrThrow(kind.equals("biome") ? Registries.BIOME : Registries.STRUCTURE)
                    .getTagNames().forEach(tag->selectors.add("#"+tag.location()));
            for(String selector:selectors) {
                var members=LocationTargets.resolve(player,kind,selector).ids().stream().map(Object::toString).sorted().toList();
                for(int offset=0;offset<Math.max(1,members.size());offset+=64)
                    pages.add(new Page(revision,kind+"|"+selector,offset,members.size(),List.copyOf(members.subList(offset,Math.min(offset+64,members.size()))),false));
            }
        }
        return List.copyOf(pages);
    }
}
