package ru.privatenull.pnlibrary.api.services

import ru.privatenull.pnlibrary.api.plugin.PluginId

/** Process-wide, platform-independent registry of typed service contracts. */
interface ServiceManager : AutoCloseable {
    /** Returns the existing owner scope or creates it atomically. */
    fun scope(owner: PluginId): ServiceScope

    /** Publishes one service provider under [owner]. */
    fun <T : Any> register(
        owner: PluginId,
        type: Class<T>,
        service: T,
        priority: Int = 0,
    ): ServiceRegistration<T> = scope(owner).publish(type, service, priority)

    /** Returns the highest-priority provider of [type], or `null`. */
    fun <T : Any> get(type: Class<T>): T?

    /** Returns the highest-priority provider or fails with a readable error. */
    fun <T : Any> require(type: Class<T>): T = get(type)
        ?: error("Service ${type.name} is not available")

    /** Returns all providers from highest to lowest priority. */
    fun <T : Any> getAll(type: Class<T>): List<T>

    /** Removes every provider owned by [owner]. */
    fun unregisterAll(owner: PluginId)

    override fun close()
}
