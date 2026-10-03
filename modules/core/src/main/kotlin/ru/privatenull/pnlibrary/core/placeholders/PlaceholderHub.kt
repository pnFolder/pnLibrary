package ru.privatenull.pnlibrary.core.placeholders

import ru.privatenull.pnlibrary.api.placeholders.*
import ru.privatenull.pnlibrary.api.plugin.PluginId
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter

/**
 * Concurrent placeholder registry shared by every plugin scope.
 *
 * The hub owns registered values, formatter definitions, and external adapter
 * bindings. Plugin-facing mutation is isolated through [Scope], allowing all
 * resources owned by one plugin to be released together.
 */
internal class PlaceholderHub(
    private val platform: PlatformAdapter,
    private val valueStore: GlobalPlaceholderValueStore = GlobalPlaceholderValueStore(),
) : PlaceholderAdapterRegistry {
    private val entries = ConcurrentHashMap<String, PlaceholderEntry<*>>()
    private val adapters = ConcurrentHashMap<String, PlaceholderAdapter>()
    private val formatterRegistry = PlaceholderFormatterRegistry()

    init {
        formatterRegistry.registerBuiltIns()
        registerSystemPlaceholders()
        registerDefaultValueCommand()
    }

    private fun registerSystemPlaceholders() {
        system("server.platform", String::class.java) { platform.type.displayName }
        system("server.implementation", String::class.java) { platform.implementationName }
        system("server.proxy", Boolean::class.javaObjectType) { platform.isProxy }
        system("runtime.java", String::class.java) {
            System.getProperty("java.version", "unknown")
        }
    }

    private fun registerDefaultValueCommand() {
        val owner = PluginId.of(SYSTEM_NAMESPACE)
        val commands = DefaultValueCommandResolver(valueStore)
        val commandKey = PlaceholderKey.of("defaultvalue", String::class.java)
        val commandEntry = PlaceholderEntry(
            owner,
            commandKey,
            { request ->
                CompletableFuture.completedFuture(
                    commands.resolve(request.parameters["value"].orEmpty(), request.playerId),
                )
            },
            null,
            PlaceholderAccess.shared(),
            PlaceholderAccess.ownerOnly(),
            cachePolicy = PlaceholderCachePolicy.none(),
            fallback = null,
            adapterLookup = ::get,
        )
        entries[id(owner, commandKey.value)] = commandEntry
        commandEntry.publish(PlaceholderPublication("placeholderapi", "pnlibrary", "defaultvalue"))
    }

    fun scope(owner: PluginId, placeholderApiEnabled: Boolean = true): PlaceholderService =
        Scope(owner, placeholderApiEnabled)

    override fun register(adapter: PlaceholderAdapter): AutoCloseable {
        val key = adapter.id.lowercase(Locale.ROOT)
        require(adapters.putIfAbsent(key, adapter) == null) { "Placeholder adapter ${adapter.id} is already registered" }
        entries.values.forEach { it.attach(adapter) }
        return AutoCloseable {
            if (adapters.remove(key, adapter)) {
                entries.values.forEach { it.detach(key) }
                if (adapter is AutoCloseable) runCatching(adapter::close)
            }
        }
    }
    override fun get(id: String): PlaceholderAdapter? = adapters[id.lowercase(Locale.ROOT)]
    override fun all(): List<PlaceholderAdapter> =
        java.util.Collections.unmodifiableList(adapters.values.sortedBy(PlaceholderAdapter::id))

    private fun <T : Any> system(key: String, type: Class<T>, value: () -> T) {
        val owner = PluginId.of(SYSTEM_NAMESPACE)
        val typedKey = PlaceholderKey.of(key, type)
        entries[id(owner, key)] = PlaceholderEntry(
            owner = owner,
            key = typedKey,
            resolver = { CompletableFuture.completedFuture(value()) },
            updater = null,
            access = PlaceholderAccess.shared(),
            updateAccess = PlaceholderAccess.ownerOnly(),
            cachePolicy = PlaceholderCachePolicy.none(),
            fallback = null,
            adapterLookup = ::get,
        )
    }

    private inner class Scope(
        private val owner: PluginId,
        private val placeholderApiEnabled: Boolean,
    ) : PlaceholderService {
        private val owned = ConcurrentHashMap.newKeySet<String>()
        private val adapterHandles = ConcurrentHashMap.newKeySet<AutoCloseable>()
        private val closed = AtomicBoolean(false)
        private val mutationLock = Any()
        private val ownedAdapters = object : PlaceholderAdapterRegistry {
            override fun register(adapter: PlaceholderAdapter): AutoCloseable = synchronized(mutationLock) {
                check(!closed.get()) { "Placeholder scope $owner is closed" }
                this@PlaceholderHub.register(adapter).also(adapterHandles::add)
            }
            override fun get(id: String) = this@PlaceholderHub.get(id)
            override fun all() = this@PlaceholderHub.all()
        }

        @Deprecated("Use register(key, configure); registration is the terminal service operation")
        override fun <T : Any> placeholder(key: PlaceholderKey<T>): PlaceholderBuilder<T> = PlaceholderDefinitionBuilder(
            owner = owner,
            key = key,
            placeholderApiEnabled = placeholderApiEnabled,
            adapterLookup = this@PlaceholderHub::get,
        ) { entry ->
            synchronized(mutationLock) {
                check(!closed.get()) { "Placeholder scope $owner is closed" }
                val full = id(owner, key.value)
                require(entries.putIfAbsent(full, entry) == null) { "Placeholder $full is already registered" }
                owned += full
                entry.whenClosed {
                    entries.remove(full, entry)
                    owned.remove(full)
                }
            }
        }

        override fun <T : Any> formatter(name: String, type: Class<T>, formatter: PlaceholderFormatter<T>) {
            synchronized(mutationLock) {
                check(!closed.get()) { "Placeholder scope $owner is closed" }
                formatterRegistry.register(owner, name, type, formatter)
            }
        }

        override fun resolve(expression: String, playerId: UUID?, values: Map<String, Any?>): CompletionStage<Any?> =
            resolveFor(owner, expression, playerId, values)

        override fun update(expression: String, value: String, playerId: UUID?, values: Map<String, Any?>): CompletionStage<Any?> =
            updateFor(owner, expression, value, playerId, values)

        override fun render(template: String, playerId: UUID?, values: Map<String, Any?>): CompletionStage<String> =
            templateRenderer(owner).render(template, playerId, values)

        override fun contains(expression: String): Boolean = find(owner, expression.substringBefore('|')).first != null
        override fun provider(pluginId: PluginId): PlaceholderProvider = object : PlaceholderProvider {
            override val pluginId = pluginId
            override fun resolve(key: String, playerId: UUID?): CompletionStage<Any?> =
                resolveFor(owner, "${pluginId.value}:$key", playerId, emptyMap())
            override fun keys(): Set<String> = java.util.Collections.unmodifiableSet(
                entries.values.filter { it.owner == pluginId && it.access.allows(pluginId, owner) }
                    .mapTo(linkedSetOf()) { it.key.value },
            )
        }
        override fun adapters(): PlaceholderAdapterRegistry = ownedAdapters
        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            val handles = synchronized(mutationLock) {
                owned.forEach { entries.remove(it)?.close() }
                owned.clear()
                formatterRegistry.removeOwnedBy(owner)
                adapterHandles.toList().also { adapterHandles.clear() }
            }
            handles.forEach { runCatching(it::close) }
        }
    }

    private fun resolveFor(consumer: PluginId, expression: String, playerId: UUID?, values: Map<String, Any?>): CompletionStage<Any?> {
        val pieces = expression.split('|')
        val reference = pieces.first().trim()
        values[reference]?.let { return CompletableFuture.completedFuture(format(consumer, it, pieces.drop(1), playerId, values)) }
        val (entry, params) = find(consumer, reference)
        if (entry == null) {
            val external = adapters.values.asSequence().filter { it.state == PlaceholderAdapterState.AVAILABLE || it.state == PlaceholderAdapterState.REGISTERED }
                .mapNotNull { adapter -> runCatching { adapter.resolve(playerId, reference) }.getOrNull() }.firstOrNull()
            return CompletableFuture.completedFuture(external)
        }
        if (!entry.access.allows(entry.owner, consumer)) return failed(SecurityException("Plugin $consumer cannot read ${entry.owner}:${entry.key.value}"))
        val request = PlaceholderRequest(entry.owner, consumer, playerId, params, values)
        @Suppress("UNCHECKED_CAST")
        return (entry as PlaceholderEntry<Any>).resolve(request).thenApply { format(consumer, it, pieces.drop(1), playerId, values, request) }
    }

    private fun updateFor(
        consumer: PluginId,
        expression: String,
        value: String,
        playerId: UUID?,
        values: Map<String, Any?>,
    ): CompletionStage<Any?> {
        val reference = expression.substringBefore('|').trim()
        val (entry, parameters) = find(consumer, reference)
        if (entry == null) return failed(IllegalArgumentException("Unknown placeholder: $reference"))
        val request = PlaceholderRequest(entry.owner, consumer, playerId, parameters, values)
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            (entry as PlaceholderEntry<Any>).update(consumer, request, value)
        }.fold(
            onSuccess = { CompletableFuture.completedFuture(it) },
            onFailure = { failed(it) },
        )
    }

    private fun templateRenderer(consumer: PluginId) = PlaceholderTemplateRenderer(
        resolve = { expression, playerId, values -> resolveFor(consumer, expression, playerId, values) },
        format = { value, pipeline, playerId, values -> format(consumer, value, pipeline, playerId, values) },
    )

    private fun find(consumer: PluginId, reference: String): Pair<PlaceholderEntry<*>?, Map<String, String>> {
        val separator = reference.indexOf(':')
        val namespace = if (separator > 0) PluginId.of(reference.substring(0, separator)) else consumer
        val key = if (separator > 0) reference.substring(separator + 1) else reference
        entries[id(namespace, key)]?.let { return it to emptyMap() }
        if (separator < 0) {
            entries[id(PluginId.of(SYSTEM_NAMESPACE), key)]?.let { return it to emptyMap() }
        }
        return entries.values.asSequence().filter { it.owner == namespace }.mapNotNull { entry ->
            PlaceholderPatternMatcher.match(entry.key.value, key)?.let { entry to it }
        }.maxByOrNull { it.first.key.value.length } ?: (null to emptyMap())
    }

    private fun format(consumer: PluginId, value: Any?, pipeline: List<String>, playerId: UUID?, values: Map<String, Any?>, existing: PlaceholderRequest? = null): Any? {
        val request = existing ?: PlaceholderRequest(consumer, consumer, playerId, emptyMap(), values)
        return formatterRegistry.format(consumer, value, pipeline, request)
    }

    private fun id(owner: PluginId, key: String): String =
        "${owner.value}:${key.lowercase(Locale.ROOT)}"

    private fun <T> failed(error: Throwable): CompletionStage<T> = CompletableFuture<T>().also { it.completeExceptionally(error) }

    private companion object {
        const val SYSTEM_NAMESPACE = "pnlibrary"
    }

}
