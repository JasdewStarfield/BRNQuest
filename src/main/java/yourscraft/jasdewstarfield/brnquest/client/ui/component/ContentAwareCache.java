package yourscraft.jasdewstarfield.brnquest.client.ui.component;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Small UI cache that reuses a value only while both its stable key and serialized content match.
 * This prevents a renamed, replaced, or cross-book editor entry from inheriting an obsolete preview.
 */
public final class ContentAwareCache<K, C, V> {
    private final Map<K, Entry<C, V>> values = new HashMap<>();

    public V get(K key, C content, Function<? super C, ? extends V> loader) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(loader, "loader");
        Entry<C, V> cached = values.get(key);
        if (cached != null && Objects.equals(cached.content(), content)) {
            return cached.value();
        }
        V value = loader.apply(content);
        values.put(key, new Entry<>(content, value));
        return value;
    }

    public void remove(K key) {
        values.remove(key);
    }

    public void clear() {
        values.clear();
    }

    private record Entry<C, V>(C content, V value) {}
}
