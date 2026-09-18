package ru.privatenull.pnlibrary.core.placeholders

import ru.privatenull.pnlibrary.api.placeholders.*
import ru.privatenull.pnlibrary.api.plugin.PluginId
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
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
    private val entries = ConcurrentHashMap<String, Entry<*>>()
    private val adapters = ConcurrentHashMap<String, PlaceholderAdapter>()
    private val formatters = ConcurrentHashMap<String, FormatterEntry<*>>()

    init {
        val system = PluginId.of("pnlibrary")
        fun builtin(name: String, formatter: (Any, List<String>) -> String) {
            formatters[formatterId(system, name)] = FormatterEntry(system, name, Any::class.java, PlaceholderFormatter { value, args, _ -> formatter(value, args) })
        }
        builtin("upper") { value, _ -> value.toString().uppercase() }
        builtin("lower") { value, _ -> value.toString().lowercase() }
        builtin("default") { value, _ -> value.toString() }
        builtin("boolean") { value, args -> if (value == true) args.getOrElse(0) { "true" } else args.getOrElse(1) { "false" } }
        builtin("plural", PlaceholderBuiltInFormatters::plural)
        builtin("duration") { value, _ -> PlaceholderBuiltInFormatters.duration(value) }
        system("server.platform", String::class.java) { platform.type.displayName }
        system("server.implementation", String::class.java) { platform.implementationName }
        system("server.proxy", Boolean::class.javaObjectType) { platform.isProxy }
        system("runtime.java", String::class.java) { System.getProperty("java.version", "unknown") }
        val commands = DefaultValueCommandResolver(valueStore)
        val commandKey = PlaceholderKey.of("defaultvalue", String::class.java)
        val commandEntry = Entry(
            system,
            commandKey,
            { request -> CompletableFuture.completedFuture(commands.resolve(request.parameters["value"].orEmpty(), request.playerId)) },
            null,
            PlaceholderAccess.shared(),
            PlaceholderAccess.ownerOnly(),
            PlaceholderCachePolicy.none(),
            null,
        )
        entries[id(system, commandKey.value)] = commandEntry
        commandEntry.publish(PlaceholderPublication("placeholderapi", "pnlibrary", "defaultvalue"))
    }

    fun scope(owner: PluginId, placeholderApiEnabled: Boolean = true): PlaceholderService =
        Scope(owner, placeholderApiEnabled)

    override fun register(adapter: PlaceholderAdapter): AutoCloseable {
        val key = adapter.id.lowercase()
        require(adapters.putIfAbsent(key, adapter) == null) { "Placeholder adapter ${adapter.id} is already registered" }
        entries.values.forEach { it.attach(adapter) }
        return AutoCloseable {
            if (adapters.remove(key, adapter)) {
                entries.values.forEach { it.detach(key) }
                if (adapter is AutoCloseable) runCatching(adapter::close)
            }
        }
    }
    override fun get(id: String): PlaceholderAdapter? = adapters[id.lowercase()]
    override fun all(): List<PlaceholderAdapter> = adapters.values.sortedBy(PlaceholderAdapter::id)

    private fun <T : Any> system(key: String, type: Class<T>, value: () -> T) {
        val owner = PluginId.of("pnlibrary")
        val typedKey = PlaceholderKey.of(key, type)
        entries[id(owner, key)] = Entry(owner, typedKey, { CompletableFuture.completedFuture(value()) }, null, PlaceholderAccess.shared(), PlaceholderAccess.ownerOnly(), PlaceholderCachePolicy.none(), null)
    }

    private inner class Scope(
        private val owner: PluginId,
        private val placeholderApiEnabled: Boolean,
    ) : PlaceholderService {
        private val owned = ConcurrentHashMap.newKeySet<String>()
        private val adapterHandles = ConcurrentHashMap.newKeySet<AutoCloseable>()
        private val closed = AtomicBoolean(false)
        private val ownedAdapters = object : PlaceholderAdapterRegistry {
            override fun register(adapter: PlaceholderAdapter): AutoCloseable =
                this@PlaceholderHub.register(adapter).also(adapterHandles::add)
            override fun get(id: String) = this@PlaceholderHub.get(id)
            override fun all() = this@PlaceholderHub.all()
        }

        override fun <T : Any> placeholder(key: PlaceholderKey<T>): PlaceholderBuilder<T> = Builder(owner, key, placeholderApiEnabled) { entry ->
            check(!closed.get()) { "Placeholder scope $owner is closed" }
            val full = id(owner, key.value)
            require(entries.putIfAbsent(full, entry) == null) { "Placeholder $full is already registered" }
            owned += full
        }

        override fun <T : Any> formatter(name: String, type: Class<T>, formatter: PlaceholderFormatter<T>) {
            require(name.matches(Regex("[a-z0-9_-]+"))) { "Invalid formatter name: $name" }
            formatters[formatterId(owner, name)] = FormatterEntry(owner, name, type, formatter)
        }

        override fun resolve(expression: String, playerId: UUID?, values: Map<String, Any?>): CompletionStage<Any?> =
            resolveFor(owner, expression, playerId, values)

        override fun update(expression: String, value: String, playerId: UUID?, values: Map<String, Any?>): CompletionStage<Any?> =
            updateFor(owner, expression, value, playerId, values)

        override fun render(template: String, playerId: UUID?, values: Map<String, Any?>): CompletionStage<String> =
            renderFor(owner, template, playerId, values)

        override fun contains(expression: String): Boolean = find(owner, expression.substringBefore('|')).first != null
        override fun provider(pluginId: PluginId): PlaceholderProvider = object : PlaceholderProvider {
            override val pluginId = pluginId
            override fun resolve(key: String, playerId: UUID?): CompletionStage<Any?> =
                resolveFor(owner, "${pluginId.value}:$key", playerId, emptyMap())
            override fun keys(): Set<String> = entries.values.filter { it.owner == pluginId && it.access.allows(pluginId, owner) }
                .map { it.key.value }.toSet()
        }
        override fun adapters(): PlaceholderAdapterRegistry = ownedAdapters
        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            owned.forEach { entries.remove(it)?.close() }
            formatters.entries.removeIf { it.value.owner == owner }
            adapterHandles.forEach { runCatching(it::close) }
            adapterHandles.clear()
        }
    }

    private inner class Builder<T : Any>(
        private val owner: PluginId,
        private val key: PlaceholderKey<T>,
        private val placeholderApiEnabled: Boolean,
        private val install: (Entry<T>) -> Unit,
    ) : PlaceholderBuilder<T> {
        private var resolver: ((PlaceholderRequest) -> CompletionStage<T?>)? = null
        private var updater: PlaceholderUpdater<T>? = null
        private var access = PlaceholderAccess.ownerOnly()
        private var updateAccess = PlaceholderAccess.ownerOnly()
        private var cache = PlaceholderCachePolicy.none()
        private var fallback: T? = null
        private val publications = mutableListOf<PlaceholderPublication>()
        override fun resolve(resolver: PlaceholderResolver<T>) = apply {
            this.resolver = { CompletableFuture.completedFuture(resolver.resolve(it)) }
        }
        override fun resolveAsync(resolver: AsyncPlaceholderResolver<T>) = apply { this.resolver = resolver::resolve }
        override fun update(updater: PlaceholderUpdater<T>) = apply { this.updater = updater }
        override fun updateAccess(access: PlaceholderAccess) = apply { this.updateAccess = access }
        override fun access(access: PlaceholderAccess) = apply { this.access = access }
        override fun access(configure: Consumer<PlaceholderAccess.Builder>) = apply {
            this.access = PlaceholderAccess.builder().also(configure::accept).build()
        }
        override fun cache(policy: PlaceholderCachePolicy) = apply { cache = policy }
        override fun fallback(value: T) = apply { fallback = value }
        override fun publish(publication: PlaceholderPublication) = apply { publications += publication }
        override fun register(): PlaceholderRegistration<T> {
            val entry = Entry(owner, key, resolver ?: error("Placeholder ${key.value} has no resolver"), updater, access, updateAccess, cache, fallback)
            install(entry)
            publications
                .filter { it.adapterId.lowercase() != "placeholderapi" || placeholderApiEnabled }
                .forEach(entry::publish)
            return entry
        }
    }

    private inner class Entry<T : Any>(
        override val owner: PluginId, override val key: PlaceholderKey<T>,
        private val resolver: (PlaceholderRequest) -> CompletionStage<T?>,
        private val updater: PlaceholderUpdater<T>?,
        val access: PlaceholderAccess, private val updateAccess: PlaceholderAccess,
        private val policy: PlaceholderCachePolicy, private val fallback: T?,
    ) : PlaceholderRegistration<T> {
        private val enabled = AtomicBoolean(true)
        private val cache = synchronizedMap<String, CacheValue<T>>()
        private val publicationBindings = java.util.Collections.synchronizedList(mutableListOf<PublicationBinding>())
        override val publications: List<ExternalPlaceholderRegistration> get() = synchronized(publicationBindings) {
            publicationBindings.toList()
        }
        override val isEnabled: Boolean get() = enabled.get()
        override fun enable() { enabled.set(true) }
        override fun disable() { enabled.set(false) }
        override fun invalidateCache() = cache.clear()
        fun resolve(request: PlaceholderRequest): CompletionStage<T?> {
            if (!isEnabled) return CompletableFuture.completedFuture(fallback)
            val cacheKey = cacheKey(request)
            cacheKey?.let { cache[it]?.takeIf { value -> !value.expired(policy) }?.let { return CompletableFuture.completedFuture(it.value) } }
            return resolver(request).thenApply { resolved ->
                val value = resolved ?: fallback
                if (cacheKey != null && (value != null || policy.cacheNullValues)) {
                    synchronized(cache) {
                        cache[cacheKey] = CacheValue(value, System.currentTimeMillis())
                        while (cache.size > policy.maximumEntries) cache.remove(cache.keys.first())
                    }
                }
                value
            }
        }
        private fun cacheKey(request: PlaceholderRequest): String? = when (policy.scope) {
            PlaceholderCacheScope.NONE -> null
            PlaceholderCacheScope.GLOBAL -> "global"
            PlaceholderCacheScope.PLUGIN -> request.consumer.value
            PlaceholderCacheScope.PLAYER -> request.playerId?.toString() ?: "no-player"
            PlaceholderCacheScope.ARGUMENTS -> request.parameters.toSortedMap().toString() + request.values.toSortedMap().toString()
        }
        fun update(consumer: PluginId, request: PlaceholderRequest, value: String): T? {
            check(updateAccess.allows(owner, consumer)) { "Plugin $consumer cannot update ${owner.value}:${key.value}" }
            val operation = updater ?: error("Placeholder ${owner.value}:${key.value} is read-only")
            val updated = operation.update(request, value)
            invalidateCache()
            return updated
        }
        fun publish(publication: PlaceholderPublication) {
            val binding = PublicationBinding(publication, owner, key.value) { request ->
                @Suppress("UNCHECKED_CAST")
                resolve(request).toCompletableFuture().join() as Any?
            }
            publicationBindings += binding
            get(publication.adapterId)?.let(binding::attach)
        }
        fun attach(adapter: PlaceholderAdapter) = publicationBindings.toList()
            .filter { it.adapterId == adapter.id.lowercase() }
            .forEach { it.attach(adapter) }
        fun detach(adapterId: String) = publicationBindings.toList()
            .filter { it.adapterId == adapterId.lowercase() }
            .forEach(PublicationBinding::detach)
        override fun close() {
            disable()
            invalidateCache()
            publicationBindings.toList().forEach { runCatching(it::close) }
            publicationBindings.clear()
        }
    }

    private class PublicationBinding(
        private val publication: PlaceholderPublication,
        private val owner: PluginId,
        private val key: String,
        private val resolver: PlaceholderResolver<Any>,
    ) : ExternalPlaceholderRegistration {
        val adapterId: String = publication.adapterId.lowercase()
        private val closed = AtomicBoolean(false)
        @Volatile private var delegate: ExternalPlaceholderRegistration? = null
        @Volatile private var failed = false

        override val state: PlaceholderAdapterState get() = when {
            closed.get() -> PlaceholderAdapterState.CLOSED
            failed -> PlaceholderAdapterState.FAILED
            delegate == null -> PlaceholderAdapterState.UNAVAILABLE
            else -> delegate!!.state
        }

        @Synchronized
        fun attach(adapter: PlaceholderAdapter) {
            if (closed.get() || adapter.id.lowercase() != adapterId) return
            delegate?.let { runCatching(it::close) }
            delegate = null
            failed = false
            runCatching { adapter.publish(owner, key, resolver, publication) }
                .onSuccess { delegate = it }
                .onFailure { failed = true }
        }

        @Synchronized
        fun detach() {
            delegate?.let { runCatching(it::close) }
            delegate = null
            failed = false
        }

        override fun close() {
            if (closed.compareAndSet(false, true)) detach()
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
        return (entry as Entry<Any>).resolve(request).thenApply { format(consumer, it, pieces.drop(1), playerId, values, request) }
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
            (entry as Entry<Any>).update(consumer, request, value)
        }.fold(
            onSuccess = { CompletableFuture.completedFuture(it) },
            onFailure = { failed(it) },
        )
    }

    private fun renderFor(consumer: PluginId, template: String, playerId: UUID?, values: Map<String, Any?>): CompletionStage<String> {
        var output = renderConditions(consumer, template, playerId, values)
        val localExpressions = Regex("\\[[^\\[\\]]+]").findAll(output)
            .map { it.value }
            .filter { LocalPlaceholderExpression.parse(it) != null }
            .distinct()
            .toList()
        localExpressions.forEach { token ->
            val expression = LocalPlaceholderExpression.parse(token) ?: return@forEach
            val resolved = resolveFor(consumer, expression.reference, playerId, values)
                .toCompletableFuture().join()
            val formatted = format(consumer, resolved, expression.formatters, playerId, values)
            output = output.replace(token, formatted?.toString() ?: token)
        }
        val expressions = Regex("\\{([^{}]+)}").findAll(output).map { it.groupValues[1] }.distinct().toList()
        var stage: CompletionStage<String> = CompletableFuture.completedFuture(output)
        expressions.forEach { expression -> stage = stage.thenCompose { current ->
            resolveFor(consumer, expression, playerId, values).thenApply { value -> current.replace("{$expression}", value?.toString() ?: "{$expression}") }
        } }
        return stage
    }

    private fun renderConditions(consumer: PluginId, source: String, playerId: UUID?, values: Map<String, Any?>): String {
        val pattern = Regex("\\{\\?([^{}]+)}([\\s\\S]*?)(?:\\{:}([\\s\\S]*?))?\\{/}")
        var result = source
        repeat(16) {
            val match = pattern.find(result) ?: return result
            val value = resolveFor(consumer, match.groupValues[1], playerId, values).toCompletableFuture().join()
            val truthy = value != null && value != false && value != 0 && value.toString().isNotBlank()
            result = result.replaceRange(match.range, if (truthy) match.groupValues[2] else match.groupValues[3])
        }
        return result
    }

    private fun find(consumer: PluginId, reference: String): Pair<Entry<*>?, Map<String, String>> {
        val separator = reference.indexOf(':')
        val namespace = if (separator > 0) PluginId.of(reference.substring(0, separator)) else consumer
        val key = if (separator > 0) reference.substring(separator + 1) else reference
        entries[id(namespace, key)]?.let { return it to emptyMap() }
        if (separator < 0) entries[id(PluginId.of("pnlibrary"), key)]?.let { return it to emptyMap() }
        return entries.values.asSequence().filter { it.owner == namespace }.mapNotNull { entry ->
            PlaceholderPatternMatcher.match(entry.key.value, key)?.let { entry to it }
        }.maxByOrNull { it.first.key.value.length } ?: (null to emptyMap())
    }

    private fun format(consumer: PluginId, value: Any?, pipeline: List<String>, playerId: UUID?, values: Map<String, Any?>, existing: PlaceholderRequest? = null): Any? {
        var current = value
        pipeline.forEach { expression ->
            val name = expression.substringBefore(':').trim()
            val args = expression.substringAfter(':', "").split(',').filter(String::isNotBlank)
            if (name == "default" && (current == null || current.toString().isBlank())) current = args.joinToString(",")
            else {
                val formatterOwner = existing?.owner ?: consumer
                val formatter = formatters[formatterId(formatterOwner, name)]
                    ?: formatters[formatterId(consumer, name)]
                    ?: formatters[formatterId(PluginId.of("pnlibrary"), name)]
                if (formatter != null && current != null && formatter.type.isInstance(current)) {
                    @Suppress("UNCHECKED_CAST")
                    current = (formatter as FormatterEntry<Any>).formatter.format(current, args, existing ?: PlaceholderRequest(consumer, consumer, playerId, emptyMap(), values))
                }
            }
        }
        return current
    }

    private data class FormatterEntry<T : Any>(val owner: PluginId, val name: String, val type: Class<T>, val formatter: PlaceholderFormatter<T>)
    private data class CacheValue<T>(val value: T?, val written: Long) { fun expired(policy: PlaceholderCachePolicy) = System.currentTimeMillis() - written >= policy.expireAfterWriteMillis }
    private fun id(owner: PluginId, key: String) = "${owner.value}:${key.lowercase()}"
    private fun formatterId(owner: PluginId, name: String) = "${owner.value}:${name.lowercase()}"
    private fun <K, V> synchronizedMap(): MutableMap<K, V> = java.util.Collections.synchronizedMap(LinkedHashMap())

    private fun <T> failed(error: Throwable): CompletionStage<T> = CompletableFuture<T>().also { it.completeExceptionally(error) }

}
