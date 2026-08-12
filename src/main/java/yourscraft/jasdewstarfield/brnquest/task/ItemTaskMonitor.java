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
                .filter(q -> q.tasks().stream().anyMatch(t -> t.typeId().getPath().equals("item")))
                // Submission tasks consume inventory only after an explicit intent;
                // dirty-event observation must never take items automatically.
                .filter(q -> q.tasks().stream().filter(t -> t.typeId().getPath().equals("item")).noneMatch(ItemTaskMonitor::consumesItems))
                .forEach(q -> ProgressEngine.get().complete(player, q.id(), false));
    }

    private static boolean consumesItems(yourscraft.jasdewstarfield.brnquest.data.TaskDefinition task) {
        String raw = task.config().getOrDefault("consume_items", task.config().getOrDefault("consume", "false"));
        return "true".equalsIgnoreCase(raw) || "1b".equalsIgnoreCase(raw);
    }
}
