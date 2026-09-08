package ru.privatenull.pnlibrary.api.services

/** Read-only access to the process-wide, platform-independent service registry. */
interface ServiceManager {
    /** Returns the highest-priority provider of [type], or `null`. */
    fun <T : Any> get(type: Class<T>): T?

    /** Returns the highest-priority provider or fails with a readable error. */
    fun <T : Any> require(type: Class<T>): T = get(type)
        ?: error("Service ${type.name} is not available")

    /** Returns all providers from highest to lowest priority. */
    fun <T : Any> getAll(type: Class<T>): List<T>
}
