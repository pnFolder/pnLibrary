package ru.privatenull.pnlibrary.api.services

/**
 * Registry of typed services shared by pnLibrary consumers.
 *
 * Providers are selected by descending priority. When priorities are equal, the provider
 * registered first is selected first. A plugin normally obtains an owner-bound manager from its
 * [ru.privatenull.pnlibrary.api.plugin.ModuleContext]; those registrations are removed together
 * when the context closes.
 *
 * Example:
 * ```kotlin
 * context.services.register(ChatFormatter::class.java, formatter, priority = 100)
 * val active = context.services.require(ChatFormatter::class.java)
 * val alternatives = context.services.getAll(ChatFormatter::class.java)
 * ```
 */
interface ServiceManager {
    /**
     * Registers [service] as a provider of [type].
     *
     * A manager accepts at most one active provider of a given type from the same owner. The
     * supplied object must be an instance of [type].
     *
     * @param type public service contract used for lookup
     * @param service implementation to register
     * @param priority selection priority; larger values are preferred
     * @throws IllegalArgumentException when [service] does not implement [type]
     * @throws IllegalStateException when the same owner already provides [type]
     */
    fun <T : Any> register(type: Class<T>, service: T, priority: Int = 0)

    /** Removes this manager's provider of [type], if present. */
    fun <T : Any> unregister(type: Class<T>)

    /** Returns the highest-priority active provider of [type], or `null` when none exists. */
    fun <T : Any> get(type: Class<T>): T?

    /**
     * Returns the highest-priority active provider of [type].
     *
     * @throws IllegalStateException when no provider is available
     */
    fun <T : Any> require(type: Class<T>): T = get(type)
        ?: error("Service ${type.name} is not available")

    /** Returns all active providers of [type], ordered by priority and registration time. */
    fun <T : Any> getAll(type: Class<T>): List<T>
}
