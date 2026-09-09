package yourscraft.jasdewstarfield.brnquest.network;

import yourscraft.jasdewstarfield.brnquest.task.advancement.AdvancementTargets;
import yourscraft.jasdewstarfield.brnquest.task.advancement.AdvancementConfig;

import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import yourscraft.jasdewstarfield.brnquest.BRNQuest;
import yourscraft.jasdewstarfield.brnquest.BrnQuestConstants;
import java.util.*;

/** Module-owned, server-to-client display data; ordinary players never use author-only field queries. */
@EventBusSubscriber(modid = BRNQuest.MOD_ID)
public final class AdvancementGroupNetwork {
    private static final Gson JSON = new Gson();
    private AdvancementGroupNetwork() {}
    public record Page(String revision, String group, int offset, int total, List<String> members, String error, boolean reset) {}
    public record Payload(String json) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(ResourceLocation.parse("brnquest:advancement_groups"));
        public static final StreamCodec<ByteBuf,Payload> CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(131072),Payload::json,Payload::new);
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(BrnQuestConstants.NETWORK_PROTOCOL).playToClient(Payload.TYPE,Payload.CODEC,(payload,context) ->
                ClientDelegate.receive(payload));
    }
    /** Keep client classes behind a lazy boundary when the module registers on a dedicated server. */
    private static class ClientDelegate {
        static void receive(Payload payload) {
            yourscraft.jasdewstarfield.brnquest.client.ui.AdvancementGroupCache.receive(JSON.fromJson(payload.json(),Page.class));
        }
    }
    /** Datapack synchronization covers both login and reload, including removal of old groups. */
    @SubscribeEvent public static void sync(OnDatapackSyncEvent event) {
        var players = event.getPlayer() == null ? event.getPlayerList().getPlayers() : List.of(event.getPlayer());
        // Match the existing transport boundary: mock players have no negotiated client channel.
        players = players.stream().filter(player -> player.connection != null
                && net.neoforged.neoforge.network.registration.NetworkRegistry.hasChannel(player.connection,Payload.TYPE.id())).toList();
        if (players.isEmpty()) return;
        for (var page : snapshot(players.getFirst().server)) for (var player : players)
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,new Payload(JSON.toJson(page)));
    }
    /** Build the same read-only snapshot for login and reload; no player permission or progress is involved. */
    public static List<Page> snapshot(net.minecraft.server.MinecraftServer server) {
        String revision = UUID.randomUUID().toString();
        var pages = new ArrayList<Page>();
        pages.add(new Page(revision,"",0,0,List.of(),"",true));
        for (String group : AdvancementTargets.choices(server).stream().filter(id -> id.startsWith("#")).sorted().toList()) {
            try {
                var members = AdvancementTargets.resolve(server,new AdvancementConfig(group,"",false)).stream().map(holder -> holder.id().toString()).toList();
                pages.addAll(pages(revision,group,members));
            } catch (IllegalArgumentException error) {
                pages.add(new Page(revision,group,0,0,List.of(),"invalid",false));
            }
        }
        return List.copyOf(pages);
    }
    /** Split large groups into bounded packets without truncating the member list. */
    public static List<Page> pages(String revision, String group, List<String> members) {
        var pages = new ArrayList<Page>();
        for (int offset=0; offset<members.size(); offset+=64)
            pages.add(new Page(revision,group,offset,members.size(),List.copyOf(members.subList(offset,Math.min(offset+64,members.size()))),"",false));
        return pages;
    }
}
