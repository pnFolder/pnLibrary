package org.pnfolder.pnlibrary.updates.engine

import org.pnfolder.pnlibrary.api.updates.ProductId
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** Persists per-component update pauses, as the freezes section of [store], and drops them once they expire. */
internal class FreezeStore(
    private val store: UpdatePreferencesStore,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val expiries = load()

    @Synchronized
    fun freeze(component: ProductId, duration: Duration): Instant {
        FreezeDuration.validate(duration)
        val expiry = clock.instant().plus(duration)
        expiries[component] = expiry
        persist()
        return expiry
    }

    @Synchronized
    fun clear(component: ProductId): Boolean {
        val removed = expiries.remove(component) != null
        if (removed) persist()
        return removed
    }

    @Synchronized
    fun remaining(component: ProductId): Duration? {
        removeExpired()
        val expiry = expiries[component] ?: return null
        return Duration.between(clock.instant(), expiry)
    }

    @Synchronized
    fun isFrozen(component: ProductId): Boolean = remaining(component) != null

    @Synchronized
    fun active(): Map<ProductId, Instant> {
        removeExpired()
        return expiries.toMap()
    }

    private fun removeExpired() {
        val now = clock.instant()
        val changed = expiries.entries.removeIf { !it.value.isAfter(now) }
        if (changed) persist()
    }

    private fun load(): MutableMap<ProductId, Instant> {
        // A pause saved under an older, longer limit still ends within the current maximum.
        val latest = clock.instant().plus(FreezeDuration.MAXIMUM)
        return store.current().freezes.entries.associateTo(linkedMapOf()) {
            ProductId.of(it.key) to minOf(Instant.ofEpochMilli(it.value), latest)
        }
    }

    private fun persist() {
        val stored = expiries.entries.associate { it.key.value to it.value.toEpochMilli() }
        store.update { it.copy(freezes = stored) }
    }
}
