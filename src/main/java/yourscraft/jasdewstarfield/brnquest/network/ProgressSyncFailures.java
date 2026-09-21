package yourscraft.jasdewstarfield.brnquest.network;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import yourscraft.jasdewstarfield.brnquest.BRNQuest;

/** Connection-scoped failures only: repeated polling must not flood logs or client chat. */
@EventBusSubscriber(modid = BRNQuest.MOD_ID)
public final class ProgressSyncFailures {
    private static final ProgressSyncFailures INSTANCE = new ProgressSyncFailures();
    private final Map<UUID, Failure> failures = new LinkedHashMap<>();
    public record Failure(String revision, String code, int bytes) {}
    public static ProgressSyncFailures get() { return INSTANCE; }

    /** Update the latest size, but notify only when entering a different failure episode. */
    synchronized boolean rejected(UUID player, String revision, String code, int bytes) {
        Failure previous = failures.remove(player);
        failures.put(player, new Failure(revision, code, bytes));
        if (failures.size() > 256) failures.remove(failures.keySet().iterator().next());
        return previous == null || !previous.revision().equals(revision) || !previous.code().equals(code);
    }
    public synchronized Optional<Failure> inspect(UUID player) { return Optional.ofNullable(failures.get(player)); }
    synchronized void forget(UUID player) { failures.remove(player); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { INSTANCE.forget(event.getEntity().getUUID()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { synchronized (INSTANCE) { INSTANCE.failures.clear(); } }
}
