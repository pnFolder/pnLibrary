package org.pnfolder.pnlibrary.api.placeholders

import org.pnfolder.pnlibrary.api.placeholders.model.PlaceholderAdapterRegistry
import org.pnfolder.pnlibrary.api.placeholders.model.PlaceholderKey
import org.pnfolder.pnlibrary.api.plugin.PluginId
import java.util.UUID
import java.util.concurrent.CompletionStage
import java.util.function.Consumer

/**
 * Plugin-scoped registry and resolution facade for typed placeholders.
 *
 * Implementations own all builders, formatters, and adapters registered through this
 * scope. Closing the service releases those resources and rejects new registrations.
 * Registration, resolution, cache invalidation, adapter lookup, and shutdown are safe from
 * arbitrary threads. Resolver and formatter callbacks may run concurrently and must therefore be
 * thread-safe. Returned completion stages finish on the resolver-selected thread.
 */
interface PlaceholderService : AutoCloseable {
    /** Configures and atomically registers one placeholder owned by this service. */
    fun <T : Any> register(
        key: PlaceholderKey<T>,
        configure: Consumer<PlaceholderBuilder<T>>,
    ): PlaceholderRegistration<T>
    /** Java-friendly overload that constructs [PlaceholderKey] from [name] and [type]. */
    fun <T : Any> register(
        name: String,
        type: Class<T>,
        configure: Consumer<PlaceholderBuilder<T>>,
    ): PlaceholderRegistration<T> = register(PlaceholderKey.of(name, type), configure)
    /** Registers a named formatter for values assignable to [type]. */
    fun <T : Any> formatter(name: String, type: Class<T>, formatter: PlaceholderFormatter<T>)
    /** Resolves one expression asynchronously, or completes with `null` when unknown. */
    fun resolve(expression: String, playerId: UUID? = null, values: Map<String, Any?> = emptyMap()): CompletionStage<Any?>
    /** Updates a writable placeholder visible to this plugin. */
    fun update(expression: String, value: String, playerId: UUID? = null, values: Map<String, Any?> = emptyMap()): CompletionStage<Any?>
    /** Resolves every placeholder embedded in [template] and returns the rendered text. */
    fun render(template: String, playerId: UUID? = null, values: Map<String, Any?> = emptyMap()): CompletionStage<String>
    /** Returns whether [expression] identifies a visible registered placeholder. */
    fun contains(expression: String): Boolean
    /** Returns the placeholder provider through which the plugin [pluginId] registers its placeholders. */
    fun provider(pluginId: PluginId): PlaceholderProvider
    /** Returns the adapter registry owned by this plugin scope. */
    fun adapters(): PlaceholderAdapterRegistry
}
