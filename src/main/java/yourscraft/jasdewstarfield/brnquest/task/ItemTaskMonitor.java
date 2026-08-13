package yourscraft.jasdewstarfield.brnquest.task;

import net.minecraft.server.level.ServerPlayer;
import yourscraft.jasdewstarfield.brnquest.progress.ProgressEngine;
import yourscraft.jasdewstarfield.brnquest.progress.QuestStatus;
import yourscraft.jasdewstarfield.brnquest.runtime.QuestBookManager;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Recomputes item quests only for players dirtied by inventory-related events. */
public final class ItemTaskMonitor {
    private static final Set<UUID> DIRTY = ConcurrentHashMap.newKeySet();
    private ItemTaskMonitor() {}
    public static void mark(ServerPlayer player) { DIRTY.add(player.getUUID()); }
    public static void tick(ServerPlayer player) {
        if (player.tickCount % 20 != 0 || !DIRTY.remove(player.getUUID())) return;
        var snapshot = QuestBookManager.get().active().orElse(null);
        if (snapshot == null) return;
        var progress = ProgressEngine.get().progress(player);
        snapshot.book().quests().stream()
                .filter(q -> progress.status(q.id().toString()) == QuestStatus.AVAILABLE || progress.status(q.id().toString()) == QuestStatus.ACTIVE)
                // Task types opt into this trigger; inventory observation therefore stays
                // independent of built-in IDs and never consumes submission-only items.
                .filter(q -> q.tasks().stream().anyMatch(ItemTaskMonitor::observesInventory))
                .forEach(q -> ProgressEngine.get().complete(player, q.id(), false));
    }

    private static boolean observesInventory(yourscraft.jasdewstarfield.brnquest.data.TaskDefinition task) {
        TaskType<?> type = TaskTypeRegistry.get(task.typeId());
        return type != null && type.reevaluateOnInventoryChangeDecoded(task);
    }
}
