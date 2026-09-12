package yourscraft.jasdewstarfield.brnquest.client;

import java.util.HashMap;
import java.util.Map;

/** Local duplicate-click guard only; expiry permits an explicit retry and never infers server success. */
final class ClientActionWait {
    private static final long WAIT_NANOS = 5_000_000_000L;
    private final Map<String, Long> sent = new HashMap<>();

    boolean begin(String key, long now) {
        if (pending(key, now)) return false;
        sent.put(key, now);
        return true;
    }

    boolean pending(String key, long now) {
        Long started = sent.get(key);
        return started != null && now - started < WAIT_NANOS;
    }

    boolean expired(String key, long now) { return sent.containsKey(key) && !pending(key, now); }
    void finish(String key) { sent.remove(key); }
    void clear() { sent.clear(); }
}
