package org.pnfolder.pnlibrary.updates.engine.preferences;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Persists per-component update pauses, as the freezes section of the preferences store, and drops them once they expire. */
public final class FreezeStore {
    private final UpdatePreferencesStore store;
    private final Clock clock;
    private final Map<ProductId, Instant> expiries;

    public FreezeStore(@NotNull UpdatePreferencesStore store) {
        this(store, Clock.systemUTC());
    }

    public FreezeStore(@NotNull UpdatePreferencesStore store, @NotNull Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = clock;
        this.expiries = load();
    }

    /** Pauses updates of {@code component} for {@code duration} and returns when the pause ends. */
    @NotNull
    public synchronized Instant freeze(@NotNull ProductId component, @NotNull Duration duration) {
        FreezeDuration.validate(duration);
        Instant expiry = clock.instant().plus(duration);
        expiries.put(component, expiry);
        persist();
        return expiry;
    }

    /** Lifts the pause of {@code component}; returns whether there was one. */
    public synchronized boolean clear(@NotNull ProductId component) {
        boolean removed = expiries.remove(component) != null;
        if (removed) {
            persist();
        }
        return removed;
    }

    /** Time left of the pause of {@code component}, or null when it is not paused. */
    @Nullable
    public synchronized Duration remaining(@NotNull ProductId component) {
        removeExpired();
        Instant expiry = expiries.get(component);
        return expiry == null ? null : Duration.between(clock.instant(), expiry);
    }

    public synchronized boolean isFrozen(@NotNull ProductId component) {
        return remaining(component) != null;
    }

    /** Every active pause with its expiry. */
    @NotNull
    public synchronized Map<ProductId, Instant> active() {
        removeExpired();
        return new LinkedHashMap<>(expiries);
    }

    private void removeExpired() {
        Instant now = clock.instant();
        if (expiries.entrySet().removeIf(entry -> !entry.getValue().isAfter(now))) {
            persist();
        }
    }

    private Map<ProductId, Instant> load() {
        // A pause saved under an older, longer limit still ends within the current maximum.
        Instant latest = clock.instant().plus(FreezeDuration.MAXIMUM);
        Map<ProductId, Instant> loaded = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : store.current().getFreezes().entrySet()) {
            Instant expiry = Instant.ofEpochMilli(entry.getValue());
            loaded.put(ProductId.of(entry.getKey()), expiry.compareTo(latest) <= 0 ? expiry : latest);
        }
        return loaded;
    }

    private void persist() {
        Map<String, Long> stored = new LinkedHashMap<>();
        for (Map.Entry<ProductId, Instant> entry : expiries.entrySet()) {
            stored.put(entry.getKey().getValue(), entry.getValue().toEpochMilli());
        }
        store.update(preferences -> preferences.withFreezes(stored));
    }
}
