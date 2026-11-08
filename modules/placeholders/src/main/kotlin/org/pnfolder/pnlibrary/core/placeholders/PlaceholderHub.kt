package org.pnfolder.pnlibrary.core.placeholders

import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapter
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapterRegistry
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapterState
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderBuilder
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderFormatter
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderKey
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderProvider
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderRegistration
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderRequest
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService
import org.pnfolder.pnlibrary.api.plugin.PluginId
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity
import java.util.Collections
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

/**
 * Concurrent placeholder registry shared by every plugin scope.
 *
 * The hub owns registered values, formatter definitions, and external adapter
 * bindings. Plugin-facing mutation is isolated through [Scope], allowing all
 * resources owned by one plugin to be released together.
 */
class PlaceholderHub(
    private val platform: PlatformIdentity,
    private val valueStore: GlobalPlaceholderValueStore = GlobalPlaceholderValueStore(),
    /** Receives the text of every problem worth the administrator's attention; each distinct one once. */
    private val issues: (String) -> Unit = {},
) : PlaceholderAdapterRegistry {
    private val entries = ConcurrentHashMap<String, PlaceholderEntry<*>>()
    private val adapters = ConcurrentHashMap<String, PlaceholderAdapter>()
    private val formatterRegistry = PlaceholderFormatterRegistry()
    private val entryLookup = PlaceholderEntryLookup(entries, PluginId.of(SYSTEM_NAMESPACE))
    private val addresses = PlaceholderAddressIndex()
    private val builtIn = BuiltInPlaceholders(entries, addresses, ::get)
    private val pluginOf = ConcurrentHashMap<PluginId, String>()
    private val reported = ConcurrentHashMap.newKeySet<String>()
    private val prefixOwners = ConcurrentHashMap<String, String>()

    init {
        formatterRegistry.registerBuiltIns()
        builtIn.registerAll(platform, valueStore)
    }

    /** [plugin] and [module] give the scope's placeholders a readable `plugin/module:name` address. */
    fun scope(
        owner: PluginId,
        placeholderApiEnabled: Boolean = true,
        plugin: String? = null,
        module: String = PlaceholderAddress.DEFAULT_MODULE,
    ): PlaceholderService = Scope(owner, placeholderApiEnabled, plugin, module).also { if (plugin != null) pluginOf[owner] = plugin }

    /** Tells the administrator once about [message]. */
    fun report(message: String) {
        if (reported.size < MAXIMUM_REPORTED && reported.add(message)) issues(message)
    }

    private fun find(consumer: PluginId, reference: String): PlaceholderEntryLookup.Match {
        PlaceholderAddress.parse(reference)?.let { return addresses.find(it) }
        if (reference.contains('/')) return PlaceholderEntryLookup.Match(null, emptyMap())
        val local = entryLookup.find(consumer, reference)
        if (local.entry != null || reference.contains(':')) return local
        return pluginOf[consumer]?.let { addresses.findInPlugin(it, reference) } ?: local
    }

    /** Every registered placeholder for `/pn placeholders`. */
    fun describe(): List<PlaceholderInfo> {
        val byAddress = addresses.all().associate { (address, entry) -> entry to address.toString() }
        return entries.values.map { entry ->
            PlaceholderInfo(
                address = byAddress[entry] ?: "${entry.owner.value}:${entry.key.value}",
                type = entry.key.type.simpleName,
                format = entry.defaultFormat,
                published = entry.publishedAs.map { publication -> publication.namespace.orEmpty() + "_" + publication.name.orEmpty() },
                enabled = entry.isEnabled,
            )
        }.sortedBy(PlaceholderInfo::address)
    }

    /** Adds a placeholder of the library itself under `pnlibrary/main:key`, visible to every plugin. */
    fun <T : Any> library(key: String, type: Class<T>, value: () -> T) = builtIn.add(key, type, value)

    override fun register(adapter: PlaceholderAdapter): AutoCloseable {
        val key = adapter.id.lowercase(Locale.ROOT)
        require(adapters.putIfAbsent(key, adapter) == null) { "Placeholder adapter ${adapter.id} is already registered" }
        entries.values.forEach { it.attach(adapter) }
        return AutoCloseable {
            if (adapters.remove(key, adapter)) {
                entries.values.forEach { it.detach(key) }
                if (adapter is AutoCloseable) runCatchingNonFatal(adapter::close)
            }
        }
    }
    override fun get(id: String): PlaceholderAdapter? = adapters[id.lowercase(Locale.ROOT)]
    override fun all(): List<PlaceholderAdapter> =
        Collections.unmodifiableList(adapters.values.sortedBy(PlaceholderAdapter::id))

    private inner class Scope(
        private val owner: PluginId,
        private val placeholderApiEnabled: Boolean,
        private val plugin: String?,
        private val module: String,
    ) : PlaceholderService {
        private val owned = ConcurrentHashMap.newKeySet<String>()
        private val prefixes = ConcurrentHashMap.newKeySet<String>()
        private val ownerName = plugin ?: owner.value

        private fun claimPrefix(prefix: String): Boolean {
            val holder = prefixOwners.putIfAbsent(prefix, ownerName) ?: ownerName
            if (holder != ownerName) {
                report("PlaceholderAPI prefix '$prefix' already belongs to $holder, so $ownerName does not publish under it")
                return false
            }
            prefixes += prefix
            return true
        }
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

        override fun <T : Any> register(
            key: PlaceholderKey<T>,
            configure: Consumer<PlaceholderBuilder<T>>,
        ): PlaceholderRegistration<T> = PlaceholderDefinitionBuilder(
            owner = owner,
            key = key,
            placeholderApiEnabled = placeholderApiEnabled,
            adapterLookup = this@PlaceholderHub::get,
            formatApplier = { value, name, request, own -> formatterRegistry.format(owner, value, listOf(name), request, own) },
            claimPrefix = ::claimPrefix,
        ) { entry ->
            synchronized(mutationLock) {
                check(!closed.get()) { "Placeholder scope $owner is closed" }
                val full = id(owner, key.value)
                require(entries.putIfAbsent(full, entry) == null) { "Placeholder $full is already registered" }
                owned += full
                val address = plugin?.let { PlaceholderAddress.of(it, module, key.value) }
                if (address != null && !addresses.add(address, entry)) {
                    entries.remove(full, entry)
                    owned.remove(full)
                    throw IllegalArgumentException("Placeholder $address is already registered")
                }
                entry.whenClosed {
                    entries.remove(full, entry)
                    owned.remove(full)
                    if (address != null) addresses.remove(address, entry)
                }
            }
        }.also(configure::accept).install()

        override fun <T : Any> formatter(name: String, type: Class<T>, formatter: PlaceholderFormatter<T>) {
            synchronized(mutationLock) {
                check(!closed.get()) { "Placeholder scope $owner is closed" }
                formatterRegistry.register(owner, name, type, formatter)
                plugin?.let { formatterRegistry.registerAddress(owner, "$it/$module:$name", type, formatter) }
            }
        }

        override fun resolve(expression: String, playerId: UUID?, values: Map<String, Any?>): CompletionStage<Any?> =
            resolveFor(owner, expression, playerId, values)

        override fun update(expression: String, value: String, playerId: UUID?, values: Map<String, Any?>): CompletionStage<Any?> =
            updateFor(owner, expression, value, playerId, values)

        override fun render(template: String, playerId: UUID?, values: Map<String, Any?>): CompletionStage<String> =
            templateRenderer(owner).render(template, playerId, values).thenApply { text -> renderExternal(text, playerId) }

        override fun contains(expression: String): Boolean =
            find(owner, expression.substringBefore('|')).entry != null
        override fun provider(pluginId: PluginId): PlaceholderProvider = object : PlaceholderProvider {
            override val pluginId = pluginId
            override fun resolve(key: String, playerId: UUID?): CompletionStage<Any?> =
                resolveFor(owner, "${pluginId.value}:$key", playerId, emptyMap())
            override fun keys(): Set<String> = Collections.unmodifiableSet(
                entries.values.filter { it.owner == pluginId && it.access.allows(pluginId, owner) }
                    .mapTo(linkedSetOf()) { it.key.value },
            )
        }
        override fun adapters(): PlaceholderAdapterRegistry = ownedAdapters
        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            pluginOf.remove(owner)
            prefixes.forEach { prefixOwners.remove(it, ownerName) }
            val handles = synchronized(mutationLock) {
                owned.forEach { entries.remove(it)?.close() }
                owned.clear()
                formatterRegistry.removeOwnedBy(owner)
                adapterHandles.toList().also { adapterHandles.clear() }
            }
            handles.forEach { runCatchingNonFatal(it::close) }
        }
    }

    private fun resolveFor(consumer: PluginId, expression: String, playerId: UUID?, values: Map<String, Any?>): CompletionStage<Any?> {
        val pieces = expression.split('|')
        val reference = pieces.first().trim()
        values[reference]?.let { return CompletableFuture.completedFuture(format(consumer, it, pieces.drop(1), playerId, values)) }
        val (entry, params) = find(consumer, reference)
        if (entry == null) {
            val external = resolveExternally(playerId, reference)
            if (external == null) report("Placeholder {$reference} is not registered (asked by $consumer)")
            return CompletableFuture.completedFuture(external)
        }
        if (!entry.access.allows(entry.owner, consumer)) {
            report("Plugin $consumer may not read {$reference}")
            return failed(SecurityException("Plugin $consumer cannot read ${entry.owner}:${entry.key.value}"))
        }
        val request = PlaceholderRequest(entry.owner, consumer, playerId, params, values)
        val pipeline = PlaceholderFormatterRegistry.pipelineOf(entry.defaultFormat, pieces.drop(1))
        @Suppress("UNCHECKED_CAST")
        return (entry as PlaceholderEntry<Any>).resolve(request)
            .thenApply { format(consumer, it, pipeline, playerId, values, request, entry.formats) }
            .exceptionally { error ->
                report("Placeholder {$reference} failed: ${error.cause?.message ?: error.message}")
                null
            }
    }

    private fun resolveExternally(playerId: UUID?, reference: String): Any? =
        adapters.values.asSequence()
            .filter { it.state == PlaceholderAdapterState.AVAILABLE || it.state == PlaceholderAdapterState.REGISTERED }
            .mapNotNull { adapter -> runCatchingNonFatal { adapter.resolve(playerId, reference) }.getOrNull() }
            .firstOrNull()

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
        return runCatchingNonFatal {
            @Suppress("UNCHECKED_CAST")
            (entry as PlaceholderEntry<Any>).update(consumer, request, value)
        }.fold(
            onSuccess = { CompletableFuture.completedFuture(it) },
            onFailure = { failed(it) },
        )
    }

    /** Lets every available outside system (PlaceholderAPI) replace what is left of its own tokens. */
    private fun renderExternal(text: String, playerId: UUID?): String = adapters.values
        .filter { it.state == PlaceholderAdapterState.AVAILABLE || it.state == PlaceholderAdapterState.REGISTERED }
        .fold(text) { current, adapter -> runCatchingNonFatal { adapter.render(playerId, current) }.getOrDefault(current) }

    private fun templateRenderer(consumer: PluginId) = PlaceholderTemplateRenderer(
        resolve = { expression, playerId, values -> resolveFor(consumer, expression, playerId, values).exceptionally { null } },
        format = { value, pipeline, playerId, values -> format(consumer, value, pipeline, playerId, values) },
    )

    private fun format(
        consumer: PluginId,
        value: Any?,
        pipeline: List<String>,
        playerId: UUID?,
        values: Map<String, Any?>,
        existing: PlaceholderRequest? = null,
        own: Map<String, PlaceholderFormatterRegistry.FormatterDefinition<*>> = emptyMap(),
    ): Any? {
        val request = existing ?: PlaceholderRequest(consumer, consumer, playerId, emptyMap(), values)
        return formatterRegistry.format(consumer, value, pipeline, request, own)
    }

    private fun id(owner: PluginId, key: String): String =
        "${owner.value}:${key.lowercase(Locale.ROOT)}"

    private fun <T> failed(error: Throwable): CompletionStage<T> = CompletableFuture<T>().also { it.completeExceptionally(error) }

    private companion object {
        const val SYSTEM_NAMESPACE = PnLibraryIdentity.PRODUCT_ID
        const val MAXIMUM_REPORTED = 500
    }
}
