package yourscraft.jasdewstarfield.brnquest.task;

import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.progress.ProgressEngine;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Samples inventory changes and retries unlocked holding objectives on the server. */
public final class ItemTaskMonitor {
    private static final Set<UUID> DIRTY = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Long> LAST_INVENTORY = new ConcurrentHashMap<>();
    private ItemTaskMonitor() {}
    public static void mark(ServerPlayer player) { DIRTY.add(player.getUUID()); }
    public static void forget(ServerPlayer player) {
        DIRTY.remove(player.getUUID());
        LAST_INVENTORY.remove(player.getUUID());
    }
    public static void clear() {
        DIRTY.clear();
        LAST_INVENTORY.clear();
    }
    public static void tick(ServerPlayer player) {
        if (player.tickCount % 20 != 0) return;
        long fingerprint = fingerprint(player);
        Long previous = LAST_INVENTORY.put(player.getUUID(), fingerprint);
        // Some inventory changes have no dedicated event. Compare the 36 main slots once a
        // second and also retry periodically when a prerequisite unlocks with unchanged items.
        if (!DIRTY.remove(player.getUUID()) && previous != null && previous == fingerprint
                && player.tickCount % 100 != 0) return;
        ProgressEngine.get().submitInventoryTasks(player);
    }

    private static long fingerprint(ServerPlayer player) {
        long hash = 1;
        for (var stack : player.getInventory().items) {
            hash = 31 * hash + stack.getCount();
            hash = 31 * hash + net.minecraft.core.registries.BuiltInRegistries.ITEM.getId(stack.getItem());
            hash = 31 * hash + stack.getComponents().hashCode();
        }
        return hash;
    }
}
