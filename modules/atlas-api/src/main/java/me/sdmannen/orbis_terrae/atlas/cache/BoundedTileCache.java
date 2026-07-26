package me.sdmannen.orbis_terrae.atlas.cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public final class BoundedTileCache<K, V> {
    private final int maximumEntries;
    private final LinkedHashMap<K, V> values;
    private long hits;
    private long misses;
    private long evictions;

    public BoundedTileCache(int maximumEntries) {
        if (maximumEntries < 1) {
            throw new IllegalArgumentException("maximumEntries must be positive");
        }
        this.maximumEntries = maximumEntries;
        this.values = new LinkedHashMap<>(16, 0.75f, true);
    }

    public synchronized Optional<V> get(K key) {
        V value = values.get(key);
        if (value == null) {
            misses++;
            return Optional.empty();
        }
        hits++;
        return Optional.of(value);
    }

    public synchronized V getOrLoad(K key, Function<K, V> loader) {
        Objects.requireNonNull(loader, "loader");
        V value = values.get(key);
        if (value != null) {
            hits++;
            return value;
        }
        misses++;
        value = Objects.requireNonNull(loader.apply(key), "loader returned null");
        values.put(key, value);
        evictIfNeeded();
        return value;
    }

    public synchronized void put(K key, V value) {
        values.put(Objects.requireNonNull(key), Objects.requireNonNull(value));
        evictIfNeeded();
    }

    public synchronized CacheStats stats() {
        return new CacheStats(values.size(), maximumEntries, hits, misses, evictions);
    }

    public synchronized void clear() {
        values.clear();
    }

    private void evictIfNeeded() {
        while (values.size() > maximumEntries) {
            Map.Entry<K, V> eldest = values.entrySet().iterator().next();
            values.remove(eldest.getKey());
            evictions++;
        }
    }

    public record CacheStats(int size, int maximumEntries, long hits, long misses, long evictions) {
    }
}
