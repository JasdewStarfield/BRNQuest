package yourscraft.jasdewstarfield.brnquest.builtin.network;
import yourscraft.jasdewstarfield.brnquest.network.*;
import yourscraft.jasdewstarfield.brnquest.builtin.reward.*;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import yourscraft.jasdewstarfield.brnquest.reward.*;
import yourscraft.jasdewstarfield.brnquest.builtin.reward.table.*;
import java.util.*;

/** Bounded read-only status protocol; requesting a preview/status never creates or advances an attempt. */
@net.neoforged.fml.common.EventBusSubscriber(modid = "brnquest")
public final class RewardTableNetwork {
    /** Built-in protocol registration is owned by this module, preserving the existing wire version. */
    @net.neoforged.bus.api.SubscribeEvent
    public static void registerPayloads(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
        register(event.registrar(yourscraft.jasdewstarfield.brnquest.BrnQuestConstants.NETWORK_PROTOCOL));
    }
    private RewardTableNetwork() {}
    private static final Map<ServerPlayer, long[]> RATES = new WeakHashMap<>();
    public record Status(String revision, String reward, String state) implements CustomPacketPayload {
        public static final Type<Status> TYPE = new Type<>(ResourceLocation.parse("brnquest:reward_table_status"));
        public static final StreamCodec<ByteBuf,Status> CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(128),Status::revision,
                ByteBufCodecs.stringUtf8(256),Status::reward,ByteBufCodecs.stringUtf8(64),Status::state,Status::new);
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Query(String revision, String reward) implements CustomPacketPayload {
        public static final Type<Query> TYPE = new Type<>(ResourceLocation.parse("brnquest:reward_table_query"));
        public static final StreamCodec<ByteBuf,Query> CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(128),Query::revision,
                ByteBufCodecs.stringUtf8(256),Query::reward,Query::new);
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public static void register(PayloadRegistrar registrar) {
        RewardTableChoiceNetwork.register(registrar);
        registrar.playToServer(Query.TYPE,Query.CODEC,(query,context) -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            // Read-only UI queries continue during pause, when the integrated server's tick counter is frozen.
            long now = System.nanoTime(); var rate = RATES.computeIfAbsent(player, ignored -> new long[]{now,0});
            if (now - rate[0] >= 1_000_000_000L || now < rate[0]) { rate[0] = now; rate[1] = 0; }
            if (++rate[1] > 8) return;
            var identity = yourscraft.jasdewstarfield.brnquest.api.BrnQuestApi.getRewardClaimState(player, query.reward()).orElse(null);
            if (identity == null || !identity.revision().equals(query.revision())) return;
            var reward = identity.context().rewardContext().reward();
            if (reward.typeId().equals(RewardTableReward.ID) || reward.typeId().equals(LootTableReward.ID)) {
                String state;
                try {
                    var claim = identity.context();
                    var attempt = RewardTableService.journal(player).read(RewardTableService.key(claim));
                    state = attempt == null ? "AVAILABLE" : attempt.state().name();
                    if (attempt != null && attempt.state() == RewardTableJournal.State.SUCCEEDED && attempt.leaves().isEmpty())
                        state = "SUCCEEDED_EMPTY";
                } catch (Exception error) { state = "BLOCKED"; }
                PacketDistributor.sendToPlayer(player,new Status(query.revision(),query.reward(),state)); return;
            }
        });
        registrar.playToClient(Status.TYPE,Status.CODEC,(payload,context) -> {
            if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT)
                ClientDelegate.receive(payload);
        });
    }
    /** Resolved only behind the physical-client guard in the receive handler. */
    private static final class ClientDelegate {
        static void receive(Status payload) {
            yourscraft.jasdewstarfield.brnquest.builtin.client.RewardTableClientState.receive(payload);
        }
    }
}
