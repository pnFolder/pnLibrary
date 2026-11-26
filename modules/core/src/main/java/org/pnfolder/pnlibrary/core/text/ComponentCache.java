package org.pnfolder.pnlibrary.core.text;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.text.cache.ComponentCachePolicy;
import org.pnfolder.pnlibrary.api.text.cache.ComponentCacheStatistics;

/** Access-ordered cache of deserialized components with hit, miss and eviction counters. */
public final class ComponentCache {
    private static final class Value {
        final Component component;
        long accessed;

        Value(Component component, long accessed) {
            this.component = component;
            this.accessed = accessed;
        }
    }

    private final Map<String, Value> values = Collections.synchronizedMap(new LinkedHashMap<String, Value>(16, .75f, true));
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();

    public @Nullable Component get(@NotNull String key, @NotNull ComponentCachePolicy policy) {
        synchronized (values) {
            Value value = values.get(key);
            if (value == null || System.currentTimeMillis() - value.accessed >= policy.getExpireAfterAccess().toMillis()) {
                if (value != null) {
                    values.remove(key);
                }
                misses.incrementAndGet();
                return null;
            }
            value.accessed = System.currentTimeMillis();
            hits.incrementAndGet();
            return value.component;
        }
    }

    public void put(@NotNull String key, @NotNull Component component, @NotNull ComponentCachePolicy policy) {
        synchronized (values) {
            values.put(key, new Value(component, System.currentTimeMillis()));
            trim(policy.getMaximumEntries());
        }
    }

    public void trim(int maximum) {
        synchronized (values) {
            while (values.size() > maximum) {
                values.remove(values.keySet().iterator().next());
                evictions.incrementAndGet();
            }
        }
    }

    public void clear() {
        values.clear();
    }

    public @NotNull ComponentCacheStatistics statistics() {
        return new ComponentCacheStatistics(hits.get(), misses.get(), evictions.get(), values.size());
    }
}
