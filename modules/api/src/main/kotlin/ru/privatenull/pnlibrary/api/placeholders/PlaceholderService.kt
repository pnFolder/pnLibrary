package ru.privatenull.pnlibrary.api.placeholders

import ru.privatenull.pnlibrary.api.plugin.PluginId
import java.util.UUID
import java.util.concurrent.CompletionStage
import java.util.function.Consumer

/**
 * Live, plugin-owned placeholder registration.
 *
 * Closing a registration disables resolution, clears its cache, and closes all external
 * publications. [close] is safe to call repeatedly.
 */
interface PlaceholderRegistration<T : Any> : AutoCloseable {
    /** Plugin that owns the resolver and its lifecycle. */
    val owner: PluginId
    /** Validated key and runtime result type. */
    val key: PlaceholderKey<T>
    /** Whether requests are currently delegated to the resolver. */
    val isEnabled: Boolean
    /** External publications, including handles waiting for unavailable adapters. */
    val publications: List<ExternalPlaceholderRegistration>
    /** Enables resolver invocation for subsequent requests. */
    fun enable()
    /** Makes subsequent requests return the fallback without invoking the resolver. */
    fun disable()
    /** Removes every cached result belonging to this registration. */
    fun invalidateCache()
}

/** Fluent definition of one typed placeholder before it enters the service registry. */
interface PlaceholderBuilder<T : Any> {
    /** Selects a synchronous resolver, replacing any previously configured resolver. */
    fun resolve(resolver: PlaceholderResolver<T>): PlaceholderBuilder<T>
    /** Selects an asynchronous resolver, replacing any previously configured resolver. */
    fun resolveAsync(resolver: AsyncPlaceholderResolver<T>): PlaceholderBuilder<T>
    /** Adds an owner-defined update operation, making this placeholder writable. */
    fun update(updater: PlaceholderUpdater<T>): PlaceholderBuilder<T>
    /** Sets the access policy used only for update operations. */
    fun updateAccess(access: PlaceholderAccess): PlaceholderBuilder<T>
    /** Sets the complete consumer access policy. */
    fun access(access: PlaceholderAccess): PlaceholderBuilder<T>
    /** Builds and sets a consumer access policy with the Java-friendly callback. */
    fun access(configure: Consumer<PlaceholderAccess.Builder>): PlaceholderBuilder<T>
    /** Configures result caching for this placeholder. */
    fun cache(policy: PlaceholderCachePolicy): PlaceholderBuilder<T>
    /** Sets the value returned for null resolver results and while disabled. */
    fun fallback(value: T): PlaceholderBuilder<T>
    /** Requests publication through an external placeholder adapter. */
    fun publish(publication: PlaceholderPublication): PlaceholderBuilder<T>
    /** Publishes with the default PlaceholderAPI namespace and name. */
    fun publishToPlaceholderApi(): PlaceholderBuilder<T> =
        publish(PlaceholderPublication("placeholderapi"))
    /** Publishes through PlaceholderAPI under [namespace]. */
    fun publishToPlaceholderApi(namespace: String): PlaceholderBuilder<T> =
        publish(PlaceholderPublication("placeholderapi", namespace))
    /** Publishes through PlaceholderAPI under explicit [namespace] and [name]. */
    fun publishToPlaceholderApi(namespace: String, name: String): PlaceholderBuilder<T> =
        publish(PlaceholderPublication("placeholderapi", namespace, name))
    /**
     * Validates and installs the definition.
     *
     * @throws IllegalStateException when no resolver was configured
     * @throws IllegalArgumentException when the owner already registered the same key
     */
    fun register(): PlaceholderRegistration<T>
}

/** Converts a resolved typed value into the string requested by a format expression. */
fun interface PlaceholderFormatter<T : Any> {
    /** Formats [value] using parsed [arguments] and the original [request]. */
    fun format(value: T, arguments: List<String>, request: PlaceholderRequest): String
}

/**
 * Applies a textual assignment to a typed placeholder-owned value.
 *
 * The owner validates and converts the supplied value, persists it in its own storage, and
 * returns the resulting typed value. Throwing rejects the update.
 */
fun interface PlaceholderUpdater<T : Any> {
    /** Updates the backing value and returns its new typed representation. */
    fun update(request: PlaceholderRequest, value: String): T?
}

/**
 * Plugin-scoped registry and resolution facade for typed placeholders.
 *
 * Implementations own all builders, formatters, and adapters registered through this
 * scope. Closing the service releases those resources and rejects new registrations.
 */
interface PlaceholderService : AutoCloseable {
    /** Starts a definition for validated [key]. */
    fun <T : Any> placeholder(key: PlaceholderKey<T>): PlaceholderBuilder<T>
    /** Starts a definition by constructing a typed key from [name] and [type]. */
    fun <T : Any> placeholder(name: String, type: Class<T>): PlaceholderBuilder<T> = placeholder(PlaceholderKey.of(name, type))
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
    /** Creates a consumer view for placeholders owned by [pluginId]. */
    fun provider(pluginId: PluginId): PlaceholderProvider
    /** Returns the adapter registry owned by this plugin scope. */
    fun adapters(): PlaceholderAdapterRegistry
}

/** Consumer view restricted to placeholders owned by one plugin. */
interface PlaceholderProvider {
    /** Plugin whose keys are addressed by this provider. */
    val pluginId: PluginId
    /** Resolves [key] as the current service owner, applying its access policy. */
    fun resolve(key: String, playerId: UUID? = null): CompletionStage<Any?>
    /** Returns the target plugin's keys visible to the current consumer. */
    fun keys(): Set<String>
}
