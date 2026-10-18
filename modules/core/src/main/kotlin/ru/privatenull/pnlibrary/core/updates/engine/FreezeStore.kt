package ru.privatenull.pnlibrary.core.updates.engine

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import ru.privatenull.pnlibrary.api.updates.ProductId
import ru.privatenull.pnlibrary.common.io.AtomicFiles
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** Persists temporary per-product update freezes with automatic expiry cleanup. */
internal class FreezeStore(
    private val path: Path,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val gson = Gson()
    private val expiries = load()

    /** Freezes [component] for [duration] and returns its expiry. */
    @Synchronized
    fun freeze(component: ProductId, duration: Duration): Instant {
        FreezeDuration.validate(duration)
        val expiry = clock.instant().plus(duration)
        expiries[component] = expiry
        persist()
        return expiry
    }

    /** Clears [component]'s freeze and reports whether one existed. */
    @Synchronized
    fun clear(component: ProductId): Boolean {
        val removed = expiries.remove(component) != null
        if (removed) persist()
        return removed
    }

    /** Returns the remaining freeze duration, or `null` when unfrozen. */
    @Synchronized
    fun remaining(component: ProductId): Duration? {
        removeExpired()
        val expiry = expiries[component] ?: return null
        return Duration.between(clock.instant(), expiry)
    }

    /** Returns whether [component] currently has an active freeze. */
    @Synchronized
    fun isFrozen(component: ProductId): Boolean = remaining(component) != null

    /** Returns a detached map of active products to expiry instants. */
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
        if (!Files.exists(path)) return linkedMapOf()
        val type = object : TypeToken<Map<String, Long>>() {}.type
        val stored: Map<String, Long> = Files.newBufferedReader(path, StandardCharsets.UTF_8).use {
            gson.fromJson(it, type) ?: emptyMap()
        }
        // A pause saved under an older, longer limit still ends within the current maximum.
        val latest = clock.instant().plus(FreezeDuration.MAXIMUM)
        return stored.entries.associateTo(linkedMapOf()) {
            ProductId.of(it.key) to minOf(Instant.ofEpochMilli(it.value), latest)
        }
    }

    private fun persist() {
        val parent = path.toAbsolutePath().parent
        Files.createDirectories(parent)
        val temporary = Files.createTempFile(parent, path.fileName.toString(), ".tmp")
        try {
            val stored = expiries.entries.associate { it.key.value to it.value.toEpochMilli() }
            Files.newBufferedWriter(temporary, StandardCharsets.UTF_8).use { gson.toJson(stored, it) }
            AtomicFiles.move(temporary, path)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
