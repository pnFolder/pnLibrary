package ru.privatenull.pnlibrary.api.placeholders

import ru.privatenull.pnlibrary.api.plugin.PluginId
import java.util.UUID
import java.util.concurrent.CompletionStage
import java.util.function.Consumer

interface PlaceholderRegistration<T : Any> : AutoCloseable {
    val owner: PluginId
    val key: PlaceholderKey<T>
    val isEnabled: Boolean
    /** External publications requested for this placeholder, including unavailable adapters. */
    val publications: List<ExternalPlaceholderRegistration>
    fun enable()
    fun disable()
    fun invalidateCache()
}

interface PlaceholderBuilder<T : Any> {
    fun resolve(resolver: PlaceholderResolver<T>): PlaceholderBuilder<T>
    fun resolveAsync(resolver: AsyncPlaceholderResolver<T>): PlaceholderBuilder<T>
    fun access(access: PlaceholderAccess): PlaceholderBuilder<T>
    fun access(configure: Consumer<PlaceholderAccess.Builder>): PlaceholderBuilder<T>
    fun cache(policy: PlaceholderCachePolicy): PlaceholderBuilder<T>
    fun fallback(value: T): PlaceholderBuilder<T>
    fun publish(publication: PlaceholderPublication): PlaceholderBuilder<T>
    fun publishToPlaceholderApi(): PlaceholderBuilder<T> =
        publish(PlaceholderPublication("placeholderapi"))
    fun publishToPlaceholderApi(namespace: String): PlaceholderBuilder<T> =
        publish(PlaceholderPublication("placeholderapi", namespace))
    fun publishToPlaceholderApi(namespace: String, name: String): PlaceholderBuilder<T> =
        publish(PlaceholderPublication("placeholderapi", namespace, name))
    fun register(): PlaceholderRegistration<T>
}

fun interface PlaceholderFormatter<T : Any> {
    fun format(value: T, arguments: List<String>, request: PlaceholderRequest): String
}

interface PlaceholderService : AutoCloseable {
    fun <T : Any> placeholder(key: PlaceholderKey<T>): PlaceholderBuilder<T>
    fun <T : Any> placeholder(name: String, type: Class<T>): PlaceholderBuilder<T> = placeholder(PlaceholderKey.of(name, type))
    fun <T : Any> formatter(name: String, type: Class<T>, formatter: PlaceholderFormatter<T>)
    fun resolve(expression: String, playerId: UUID? = null, values: Map<String, Any?> = emptyMap()): CompletionStage<Any?>
    fun render(template: String, playerId: UUID? = null, values: Map<String, Any?> = emptyMap()): CompletionStage<String>
    fun contains(expression: String): Boolean
    fun provider(pluginId: PluginId): PlaceholderProvider
    fun adapters(): PlaceholderAdapterRegistry
}

interface PlaceholderProvider {
    val pluginId: PluginId
    fun resolve(key: String, playerId: UUID? = null): CompletionStage<Any?>
    fun keys(): Set<String>
}
