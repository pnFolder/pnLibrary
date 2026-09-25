package ru.privatenull.pnlibrary.api.config

import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lifecycle group for multiple configuration files owned by one plugin.
 *
 * The owner keeps typed handles; this group only invokes their common lifecycle
 * operations.
 *
 * ```kotlin
 * val configs = ConfigGroup().add(settings).add(messages)
 * configs.loadAll()
 * ```
 */
class ConfigGroup : AutoCloseable {
    private val configs = linkedSetOf<ManagedConfig<*>>()
    private val closed = AtomicBoolean(false)

    /** Whether this lifecycle group has already been closed. */
    val isClosed: Boolean get() = closed.get()

    /** Adds [config] and returns this group for fluent calls. */
    fun add(config: ManagedConfig<*>) = apply {
        check(!closed.get()) { "Configuration group is closed" }
        configs += config
    }

    /** Returns an immutable snapshot in registration order. */
    fun all(): List<ManagedConfig<*>> = Collections.unmodifiableList(ArrayList(configs))

    /** Loads all files in registration order. */
    fun loadAll() { configs.forEach { it.load() } }

    /** Reloads all files; each handle preserves its previous value on failure. */
    fun reloadAll() { configs.forEach { it.reload() } }

    /** Saves every currently loaded configuration. */
    fun saveAll() { configs.filter { it.isLoaded }.forEach { it.save() } }

    /** Removes all values from memory without deleting their files. */
    fun unloadAll() { configs.forEach { it.unload() } }

    /** Number of registered configuration handles. */
    fun size(): Int = configs.size

    /** Unloads the entire group. */
    override fun close() {
        if (closed.compareAndSet(false, true)) unloadAll()
    }
}
