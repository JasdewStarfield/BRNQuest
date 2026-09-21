package yourscraft.jasdewstarfield.brnquest.task;

import com.mojang.serialization.DataResult;
import java.util.LinkedHashMap;
import java.util.Map;
import yourscraft.jasdewstarfield.brnquest.data.StringMapConfigCodec;

/** Bounded cache of explicitly reusable values; never retains a player, context or progress result. */
final class DecodedTaskConfigs {
    private static final int LIMIT = 4096;
    private static final Map<Key, DataResult<?>> VALUES = new LinkedHashMap<>(16, 0.75F, true);

    private record Key(TaskType<?> type, Map<String, String> config) {
        Key { config = Map.copyOf(config); }
        // Type identity matters even if two implementations override equals or reuse the same registry ID.
        @Override public boolean equals(Object other) {
            return other instanceof Key key && type == key.type && config.equals(key.config);
        }
        @Override public int hashCode() { return 31 * System.identityHashCode(type) + config.hashCode(); }
    }

    private DecodedTaskConfigs() {}

    static <T> DataResult<T> decode(TaskType<T> type, Map<String, String> config) {
        if (!(type instanceof ReusableTaskConfigType<?>)) return StringMapConfigCodec.decode(type.configCodec(), config);
        return reusable(type, config);
    }

    @SuppressWarnings("unchecked") // The same type instance owns both the key and the decoded generic value.
    private static synchronized <T> DataResult<T> reusable(TaskType<T> type, Map<String, String> config) {
        Key key = new Key(type, config);
        DataResult<?> previous = VALUES.get(key);
        if (previous != null) return (DataResult<T>) previous;
        DataResult<T> decoded = StringMapConfigCodec.decode(type.configCodec(), key.config());
        VALUES.put(key, decoded);
        if (VALUES.size() > LIMIT) VALUES.remove(VALUES.keySet().iterator().next());
        return decoded;
    }

    static synchronized void clearForTest() { VALUES.clear(); }
}
