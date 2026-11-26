package org.pnfolder.pnlibrary.bukkit.item;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/** A bounded, least-recently-used cache that stores and hands out copies, so a caller can never change what the cache holds. */
final class CopyingCache<V> {
    private final UnaryOperator<V> copy;
    private final Map<String, V> entries;

    CopyingCache(int capacity, UnaryOperator<V> copy) {
        this.copy = copy;
        this.entries = new LinkedHashMap<String, V>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, V> eldest) {
                return size() > capacity;
            }
        };
    }

    int size() {
        synchronized (entries) {
            return entries.size();
        }
    }

    V get(String key) {
        V stored;
        synchronized (entries) {
            stored = entries.get(key);
        }
        return stored == null ? null : copy.apply(stored);
    }

    void set(String key, V value) {
        V stored = copy.apply(value);
        synchronized (entries) {
            entries.put(key, stored);
        }
    }
}
