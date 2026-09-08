package ru.privatenull.pnlibrary.api.services

/** Simple platform-independent registry of typed services. */
interface ServiceManager {
    /** Registers one provider. A [ru.privatenull.pnlibrary.api.plugin.PluginContext] supplies its owner automatically. */
    fun <T : Any> register(type: Class<T>, service: T, priority: Int = 0)

    /** Removes the provider of [type] registered through this manager. */
    fun <T : Any> unregister(type: Class<T>)

    /** Returns the highest-priority provider of [type], or `null`. */
    fun <T : Any> get(type: Class<T>): T?

    /** Returns the highest-priority provider or fails with a readable error. */
    fun <T : Any> require(type: Class<T>): T = get(type)
        ?: error("Service ${type.name} is not available")

    /** Returns all providers from highest to lowest priority. */
    fun <T : Any> getAll(type: Class<T>): List<T>
}
