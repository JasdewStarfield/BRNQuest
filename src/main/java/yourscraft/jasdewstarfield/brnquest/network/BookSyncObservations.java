package yourscraft.jasdewstarfield.brnquest.network;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import yourscraft.jasdewstarfield.brnquest.BRNQuest;

/** Bounded server observations of transport submission, never a client receipt or an authorization source. */
@EventBusSubscriber(modid = BRNQuest.MOD_ID)
public final class BookSyncObservations {
    private static final BookSyncObservations INSTANCE = new BookSyncObservations();
    private static final int LIMIT = 256;
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    public enum Status { QUEUING, SUBMITTED, NOT_SUBMITTED, REJECTED, UNKNOWN }
    public record Entry(Status status, String revision, int submittedChunks, int expectedChunks,
                        int encodedBytes, String code, String observedAt) {}
    public static BookSyncObservations get() { return INSTANCE; }

    /** A missing or evicted record means unknown, not successful delivery. */
    public synchronized Entry inspect(UUID player) {
        return entries.getOrDefault(player, new Entry(Status.UNKNOWN, "", 0, 0, 0, "", ""));
    }
    synchronized void observe(UUID player, CustomPacketPayload payload, boolean submitted) {
        if (payload instanceof BrnQuestNetwork.BookManifestPayload manifest) {
            put(player, new Entry(submitted ? Status.QUEUING : Status.NOT_SUBMITTED, manifest.revision(), 0,
                    manifest.chunks(), manifest.decodedBytes(), submitted ? "" : "NOT_SUBMITTED", now()));
        } else if (payload instanceof BrnQuestNetwork.BookChunkPayload chunk) {
            var previous = entries.get(player);
            if (previous == null || !previous.revision().equals(chunk.revision()) || previous.status() == Status.REJECTED) return;
            int count = previous.submittedChunks() + (submitted ? 1 : 0);
            Status status = !submitted || previous.status() == Status.NOT_SUBMITTED ? Status.NOT_SUBMITTED
                    : count == previous.expectedChunks() ? Status.SUBMITTED : Status.QUEUING;
            put(player, new Entry(status, previous.revision(), count, previous.expectedChunks(), previous.encodedBytes(),
                    status == Status.NOT_SUBMITTED ? "NOT_SUBMITTED" : "", now()));
        }
    }
    synchronized void rejected(UUID player, String revision, String code) {
        put(player, new Entry(Status.REJECTED, revision, 0, 0, 0, code, now()));
    }
    private void put(UUID player, Entry entry) {
        entries.remove(player);
        entries.put(player, entry);
        if (entries.size() > LIMIT) entries.remove(entries.keySet().iterator().next());
    }
    synchronized void forget(UUID player) { entries.remove(player); }
    private static String now() { return java.time.Instant.now().toString(); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { INSTANCE.forget(event.getEntity().getUUID()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { synchronized (INSTANCE) { INSTANCE.entries.clear(); } }
}
