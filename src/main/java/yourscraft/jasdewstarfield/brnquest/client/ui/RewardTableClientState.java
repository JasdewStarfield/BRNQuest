package yourscraft.jasdewstarfield.brnquest.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import yourscraft.jasdewstarfield.brnquest.network.RewardTableNetwork;
import java.util.*;

/** Small bounded status cache; the server remains the only source of execution state. */
public final class RewardTableClientState {
    private RewardTableClientState() {}
    private record Cached(long queried, String state) {}
    private static final Map<String,Cached> CACHE = new LinkedHashMap<>();
    public static Component hint(String reward) {
        return hint(reward, false);
    }
    public static Component hint(String reward, boolean claimed) {
        String revision = yourscraft.jasdewstarfield.brnquest.client.ClientQuestState.get().revision();
        String key = revision + "/" + reward;
        long now = System.nanoTime(); Cached value = CACHE.get(key);
        if (value == null || now - value.queried() > 1_000_000_000L) {
            if (CACHE.size() >= 256) CACHE.remove(CACHE.keySet().iterator().next());
            CACHE.put(key,new Cached(now,value == null ? "CHECKING" : value.state()));
            PacketDistributor.sendToServer(new RewardTableNetwork.Query(revision,reward));
        }
        if (claimed && (value == null || !value.state().equals("SUCCEEDED_EMPTY")))
            return Component.translatable("screen.brnquest.reward.claimed");
        return Component.translatable("screen.brnquest.reward_table.state." + (value == null ? "CHECKING" : value.state()).toLowerCase(Locale.ROOT));
    }
    public static void receive(RewardTableNetwork.Status payload) {
        Minecraft.getInstance().execute(() -> {
            String key = payload.revision() + "/" + payload.reward();
            if (CACHE.containsKey(key)) CACHE.put(key,new Cached(System.nanoTime(),payload.state()));
        });
    }
    public static void clear() { CACHE.clear(); RewardTableChoiceScreen.clearExpected(); }
}
