package ru.privatenull.pnlibrary.api.services

import ru.privatenull.pnlibrary.api.plugin.PluginId

/** Owner-bound service view whose publications are removed together. */
interface ServiceScope : AutoCloseable {
    val owner: PluginId
    val isClosed: Boolean

    /** Publishes one implementation of [type] under this scope. */
    fun <T : Any> publish(type: Class<T>, service: T, priority: Int = 0): ServiceRegistration<T>

    /** Returns the highest-priority provider of [type], or `null`. */
    fun <T : Any> get(type: Class<T>): T?

    /** Returns the highest-priority provider or fails with a readable error. */
    fun <T : Any> require(type: Class<T>): T = get(type)
        ?: error("Service ${type.name} is not available")

    /** Returns all providers from highest to lowest priority. */
    fun <T : Any> getAll(type: Class<T>): List<T>

    /** Removes every provider published by this owner. */
    override fun close()
}
