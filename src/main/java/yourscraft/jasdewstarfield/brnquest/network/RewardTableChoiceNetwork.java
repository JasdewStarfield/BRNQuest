package yourscraft.jasdewstarfield.brnquest.network;

import com.google.gson.*;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import yourscraft.jasdewstarfield.brnquest.reward.table.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Choice requests contain identities only. Display pages never contain executable commands or complete reward configs. */
public final class RewardTableChoiceNetwork {
    private RewardTableChoiceNetwork() {}
    private static final StreamCodec<ByteBuf,String> TEXT = ByteBufCodecs.stringUtf8(256);
    private static final StreamCodec<ByteBuf,String> PATH = ByteBufCodecs.stringUtf8(1024);
    private static final Map<ServerPlayer,long[]> RATES = new WeakHashMap<>();
    public record Request(String revision, String reward, String attempt, String occurrence, int version, String entry, int offset)
            implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(ResourceLocation.parse("brnquest:table_choice_request"));
        public static final StreamCodec<ByteBuf,Request> CODEC = StreamCodec.of((b,v) -> {
            TEXT.encode(b,v.revision); TEXT.encode(b,v.reward); TEXT.encode(b,v.attempt); PATH.encode(b,v.occurrence);
            ByteBufCodecs.VAR_INT.encode(b,v.version); TEXT.encode(b,v.entry); ByteBufCodecs.VAR_INT.encode(b,v.offset);
        }, b -> new Request(TEXT.decode(b),TEXT.decode(b),TEXT.decode(b),PATH.decode(b),ByteBufCodecs.VAR_INT.decode(b),TEXT.decode(b),ByteBufCodecs.VAR_INT.decode(b)));
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Page(String revision, String reward, String attempt, int offset, int total, String entries, String state, String occurrence, int version)
            implements CustomPacketPayload {
        public Page(String revision,String reward,String attempt,int offset,int total,String entries,String state) {
            this(revision,reward,attempt,offset,total,entries,state,RewardTableChoice.PATH,0);
        }
        public static final Type<Page> TYPE = new Type<>(ResourceLocation.parse("brnquest:table_choice_page"));
        public static final StreamCodec<ByteBuf,Page> CODEC = StreamCodec.of((b,v) -> {
            TEXT.encode(b,v.revision); TEXT.encode(b,v.reward); TEXT.encode(b,v.attempt);
            ByteBufCodecs.VAR_INT.encode(b,v.offset); ByteBufCodecs.VAR_INT.encode(b,v.total);
            ByteBufCodecs.stringUtf8(32768).encode(b,v.entries); TEXT.encode(b,v.state); PATH.encode(b,v.occurrence); ByteBufCodecs.VAR_INT.encode(b,v.version);
        }, b -> new Page(TEXT.decode(b),TEXT.decode(b),TEXT.decode(b),ByteBufCodecs.VAR_INT.decode(b),ByteBufCodecs.VAR_INT.decode(b),ByteBufCodecs.stringUtf8(32768).decode(b),TEXT.decode(b),PATH.decode(b),ByteBufCodecs.VAR_INT.decode(b)));
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(Request.TYPE, Request.CODEC, (request, context) -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            long now = System.nanoTime(); var rate = RATES.computeIfAbsent(player, ignored -> new long[]{now,0});
            if (now - rate[0] >= 1_000_000_000L || now < rate[0]) { rate[0] = now; rate[1] = 0; }
            if (++rate[1] > 8) { reply(player, request, "RATE_LIMITED"); return; }
            try {
                var snapshot = yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager.get().active().orElseThrow();
                if (!snapshot.revision().equals(request.revision())) throw new IllegalArgumentException("Stale revision");
                var id = ResourceLocation.parse(request.reward());
                if (request.entry().isEmpty()) {
                    sendPage(player, id, request.attempt(), request.offset());
                } else {
                    var result = RewardTableService.confirmChoice(player, id, new RewardTableChoice.Confirmation(
                            request.attempt(), request.occurrence(), request.version(), request.entry()));
                    var attempt=RewardTableService.journal(player).read(RewardTableService.key(RewardTableService.choiceContext(player,id)));
                    if(attempt!=null && attempt.state()==RewardTableJournal.State.AWAITING_CHOICE) sendPage(player,id,request.attempt(),0);
                    else reply(player, request, result.success() ? "CONFIRMED" : result.code());
                }
            } catch (Exception error) {
                yourscraft.jasdewstarfield.brnquest.BRNQuest.LOGGER.warn("Reward table choice rejected for {}: {}", request.reward(), error.toString());
                reply(player, request, "REJECTED");
            }
        });
        registrar.playToClient(Page.TYPE, Page.CODEC, (page, context) -> {
            if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT)
                yourscraft.jasdewstarfield.brnquest.client.ui.RewardTableChoiceScreen.receive(page);
        });
    }
    private static void reply(ServerPlayer player, Request request, String state) {
        PacketDistributor.sendToPlayer(player, new Page(request.revision(),request.reward(),request.attempt(),0,0,"[]",state));
    }
    /** Called only following an explicit successful root claim that stopped at AWAITING_CHOICE. */
    public static void open(ServerPlayer player, ResourceLocation reward) {
        try { sendPage(player, reward, "", 0); }
        catch (Exception error) { yourscraft.jasdewstarfield.brnquest.BRNQuest.LOGGER.warn("Could not open reward choice", error); }
    }
    public static void sendPage(ServerPlayer player, ResourceLocation reward, String expectedAttempt, int offset) throws Exception {
        var context = RewardTableService.choiceContext(player, reward);
        var attempt = RewardTableService.journal(player).read(RewardTableService.key(context));
        if (attempt == null || !attempt.executor().equals(player.getUUID().toString())
                || (!expectedAttempt.isEmpty() && !attempt.attemptId().equals(expectedAttempt))
                || attempt.state() != RewardTableJournal.State.AWAITING_CHOICE)
            throw new IllegalArgumentException("Choice no longer available to this executor");
        var choice = RewardTablePlan.pending(attempt);
        if(choice==null) throw new IllegalArgumentException("No pending choice");
        var entries = choice.entries();
        var page = displayPage(entries, offset);
        var revision = yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager.get().active().orElseThrow().revision();
        PacketDistributor.sendToPlayer(player, new Page(revision,reward.toString(),attempt.attemptId(),offset,entries.size(),page,"AWAITING_CHOICE",choice.occurrence(),choice.version()));
    }
    /** Small display-only pages cover every index. Oversized icons fall back to the type label without losing an option. */
    public static String displayPage(List<JsonObject> entries, int offset) {
        if (offset < 0 || offset >= entries.size()) throw new IllegalArgumentException("Invalid choice page");
        var page = new JsonArray();
        for (int i = offset; i < Math.min(entries.size(), offset + 8); i++) {
            var source = entries.get(i); var entry = new JsonObject();
            entry.addProperty("id", source.get("entry_id").getAsString());
            entry.addProperty("type", source.get("type").getAsString());
            var config = RewardTableTree.config(source); var display = new JsonObject();
            // The selector lets the client resolve native advancement names/icons without sending execution settings.
            for (String key : List.of("title", "item", "count", "xp", "xp_levels", "advancement")) {
                String value = config.getOrDefault(key, "");
                if (!value.isBlank() && new JsonPrimitive(value).toString().getBytes(StandardCharsets.UTF_8).length <= (key.equals("item") ? 2048 : 256)) display.addProperty(key,value);
            }
            entry.add("config", display); page.add(entry);
        }
        String result = page.toString();
        if (result.getBytes(StandardCharsets.UTF_8).length > 32768) throw new IllegalArgumentException("Choice display exceeds packet budget");
        return result;
    }
}
