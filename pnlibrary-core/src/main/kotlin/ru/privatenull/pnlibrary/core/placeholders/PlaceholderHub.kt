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

internal class PlaceholderHub(private val platform: PlatformAdapter) : PlaceholderAdapterRegistry {
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
        builtin("plural") { value, args -> plural((value as Number).toLong(), args) }
        builtin("duration") { value, _ -> duration(value) }
        system("server.platform", String::class.java) { platform.type.displayName }
        system("server.implementation", String::class.java) { platform.implementationName }
        system("server.proxy", Boolean::class.javaObjectType) { platform.isProxy }
        system("runtime.java", String::class.java) { System.getProperty("java.version", "unknown") }
    }

    fun scope(owner: PluginId): PlaceholderService = Scope(owner)

    override fun register(adapter: PlaceholderAdapter): AutoCloseable {
        val key = adapter.id.lowercase()
        require(adapters.putIfAbsent(key, adapter) == null) { "Placeholder adapter ${adapter.id} is already registered" }
        return AutoCloseable {
            if (adapters.remove(key, adapter) && adapter is AutoCloseable) runCatching(adapter::close)
        }
    }
    override fun get(id: String): PlaceholderAdapter? = adapters[id.lowercase()]
    override fun all(): List<PlaceholderAdapter> = adapters.values.sortedBy(PlaceholderAdapter::id)

    private fun <T : Any> system(key: String, type: Class<T>, value: () -> T) {
        val owner = PluginId.of("pnlibrary")
        val typedKey = PlaceholderKey.of(key, type)
        entries[id(owner, key)] = Entry(owner, typedKey, { CompletableFuture.completedFuture(value()) }, PlaceholderAccess.shared(), PlaceholderCachePolicy.none(), null)
    }

    private inner class Scope(private val owner: PluginId) : PlaceholderService {
        private val owned = ConcurrentHashMap.newKeySet<String>()
        private val adapterHandles = ConcurrentHashMap.newKeySet<AutoCloseable>()
        private val closed = AtomicBoolean(false)
        private val ownedAdapters = object : PlaceholderAdapterRegistry {
            override fun register(adapter: PlaceholderAdapter): AutoCloseable =
                this@PlaceholderHub.register(adapter).also(adapterHandles::add)
            override fun get(id: String) = this@PlaceholderHub.get(id)
            override fun all() = this@PlaceholderHub.all()
        }

        override fun <T : Any> placeholder(key: PlaceholderKey<T>): PlaceholderBuilder<T> = Builder(owner, key) { entry ->
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
        private val owner: PluginId, private val key: PlaceholderKey<T>, private val install: (Entry<T>) -> Unit,
    ) : PlaceholderBuilder<T> {
        private var resolver: ((PlaceholderRequest) -> CompletionStage<T?>)? = null
        private var access = PlaceholderAccess.ownerOnly()
        private var cache = PlaceholderCachePolicy.none()
        private var fallback: T? = null
        private val publications = mutableListOf<PlaceholderPublication>()
        override fun resolve(resolver: PlaceholderResolver<T>) = apply {
            this.resolver = { CompletableFuture.completedFuture(resolver.resolve(it)) }
        }
        override fun resolveAsync(resolver: AsyncPlaceholderResolver<T>) = apply { this.resolver = resolver::resolve }
        override fun access(access: PlaceholderAccess) = apply { this.access = access }
        override fun access(configure: Consumer<PlaceholderAccess.Builder>) = apply {
            this.access = PlaceholderAccess.builder().also(configure::accept).build()
        }
        override fun cache(policy: PlaceholderCachePolicy) = apply { cache = policy }
        override fun fallback(value: T) = apply { fallback = value }
        override fun publish(publication: PlaceholderPublication) = apply { publications += publication }
        override fun register(): PlaceholderRegistration<T> {
            val entry = Entry(owner, key, resolver ?: error("Placeholder ${key.value} has no resolver"), access, cache, fallback)
            install(entry)
            publications.forEach { publication ->
                val adapter = get(publication.adapterId) ?: return@forEach
                @Suppress("UNCHECKED_CAST")
                entry.external += adapter.publish(owner, key.value, PlaceholderResolver { request ->
                    entry.resolve(request).toCompletableFuture().join() as Any?
                } as PlaceholderResolver<Any>, publication)
            }
            return entry
        }
    }

    private inner class Entry<T : Any>(
        override val owner: PluginId, override val key: PlaceholderKey<T>,
        private val resolver: (PlaceholderRequest) -> CompletionStage<T?>,
        val access: PlaceholderAccess, private val policy: PlaceholderCachePolicy, private val fallback: T?,
    ) : PlaceholderRegistration<T> {
        private val enabled = AtomicBoolean(true)
        private val cache = synchronizedMap<String, CacheValue<T>>()
        val external = mutableListOf<ExternalPlaceholderRegistration>()
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
        override fun close() { disable(); invalidateCache(); external.forEach { runCatching(it::close) }; external.clear() }
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

    private fun renderFor(consumer: PluginId, template: String, playerId: UUID?, values: Map<String, Any?>): CompletionStage<String> {
        var output = renderConditions(consumer, template, playerId, values)
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
            match(entry.key.value, key)?.let { entry to it }
        }.maxByOrNull { it.first.key.value.length } ?: (null to emptyMap())
    }

    private fun match(pattern: String, value: String): Map<String, String>? {
        val names = Regex("\\{([a-zA-Z0-9_-]+)}").findAll(pattern).map { it.groupValues[1] }.toList()
        if (names.isEmpty()) return null
        val token = Regex("\\{([a-zA-Z0-9_-]+)}")
        var cursor = 0
        val expression = buildString {
            append('^')
            token.findAll(pattern).forEach { found ->
                append(Regex.escape(pattern.substring(cursor, found.range.first)))
                append("([^.]+)")
                cursor = found.range.last + 1
            }
            append(Regex.escape(pattern.substring(cursor)))
            append('$')
        }
        val regex = Regex(expression)
        val match = regex.matchEntire(value) ?: return null
        return names.mapIndexed { index, name -> name to match.groupValues[index + 1] }.toMap()
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

    private fun plural(number: Long, values: List<String>): String {
        if (values.size < 3) return values.firstOrNull().orEmpty()
        val mod100 = number % 100
        val index = if (mod100 in 11..14) 2 else when (number % 10) { 1L -> 0; 2L, 3L, 4L -> 1; else -> 2 }
        return values[index]
    }

    private fun duration(value: Any): String {
        var seconds = when (value) { is java.time.Duration -> value.seconds; is Number -> value.toLong(); else -> return value.toString() }
        val days = seconds / 86400; seconds %= 86400
        val hours = seconds / 3600; seconds %= 3600
        val minutes = seconds / 60; seconds %= 60
        return listOf(days to "d", hours to "h", minutes to "m", seconds to "s")
            .filter { it.first > 0 }.joinToString(" ") { "${it.first}${it.second}" }.ifEmpty { "0s" }
    }
}
