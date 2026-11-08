package org.pnfolder.pnlibrary.core.placeholders

import org.pnfolder.pnlibrary.api.placeholders.ExternalPlaceholderRegistration
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAccess
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapter
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderCachePolicy
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderCacheScope
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderKey
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderPublication
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderRegistration
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderRequest
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderUpdater
import org.pnfolder.pnlibrary.api.plugin.PluginId
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import java.util.Collections
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/** One registered placeholder, including its cache, update policy and external publications. */
class PlaceholderEntry<T : Any>(
    override val owner: PluginId,
    override val key: PlaceholderKey<T>,
    private val resolver: (PlaceholderRequest) -> CompletionStage<T?>,
    private val updater: PlaceholderUpdater<T>?,
    val access: PlaceholderAccess,
    private val updateAccess: PlaceholderAccess,
    private val cachePolicy: PlaceholderCachePolicy,
    private val fallback: T?,
    private val adapterLookup: (String) -> PlaceholderAdapter?,
    /** Formats only this placeholder understands, by lowercase name. */
    val formats: Map<String, PlaceholderFormatterRegistry.FormatterDefinition<T>> = emptyMap(),
    /** Format applied when the text names none; `null` for a plain value. */
    val defaultFormat: String? = null,
    private val publishedFormat: ((Any?, PlaceholderRequest) -> Any?)? = null,
) : PlaceholderRegistration<T> {
    private val enabled = AtomicBoolean(true)
    private val closed = AtomicBoolean(false)
    private val cache = Collections.synchronizedMap(LinkedHashMap<String, CachedPlaceholderValue<T>>())
    private val publicationBindings = Collections.synchronizedList(mutableListOf<PlaceholderPublicationBinding>())
    private val publishedNames = mutableListOf<PlaceholderPublication>()
    private val stale: MutableMap<String, Any> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Any>() {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Any>) = size > MAXIMUM_STALE
        },
    )

    @Volatile
    private var closeCallback: (() -> Unit)? = null

    override val publications: List<ExternalPlaceholderRegistration>
        get() = synchronized(publicationBindings) {
            Collections.unmodifiableList(ArrayList(publicationBindings))
        }

    override val isEnabled: Boolean
        get() = enabled.get() && !closed.get()

    override val isClosed: Boolean
        get() = closed.get()

    override fun enable() {
        check(!closed.get()) { "Placeholder ${owner.value}:${key.value} is closed" }
        enabled.set(true)
    }

    override fun disable() {
        enabled.set(false)
    }

    override fun invalidateCache() {
        cache.clear()
    }

    /** Sets what runs once when the entry is closed, so the hub can forget it. */
    fun whenClosed(callback: () -> Unit) {
        closeCallback = callback
    }

    /** Reads the value for [request] from the cache or the resolver; a missing value becomes the fallback. */
    fun resolve(request: PlaceholderRequest): CompletionStage<T?> {
        if (!isEnabled) return CompletableFuture.completedFuture(fallback)

        val cacheKey = cacheKey(request)
        cachedValue(cacheKey)?.let { cached ->
            return CompletableFuture.completedFuture(cached.value)
        }

        return resolver(request).thenApply { resolved ->
            val value = resolved ?: fallback
            cacheValue(cacheKey, value)
            value
        }
    }

    /** Changes the value on behalf of [consumer] and clears the cache; fails when the placeholder is read-only or [consumer] may not. */
    fun update(consumer: PluginId, request: PlaceholderRequest, value: String): T? {
        check(updateAccess.allows(owner, consumer)) {
            "Plugin $consumer cannot update ${owner.value}:${key.value}"
        }
        val operation = updater ?: error("Placeholder ${owner.value}:${key.value} is read-only")
        return operation.update(request, value).also { invalidateCache() }
    }

    /** What this placeholder was asked to publish outside, for the administrator's list. */
    val publishedAs: List<PlaceholderPublication> get() = synchronized(publishedNames) { publishedNames.toList() }

    /** Publishes the placeholder to an outside system, now if its adapter is present and when it arrives otherwise. */
    fun publish(publication: PlaceholderPublication) {
        synchronized(publishedNames) { publishedNames += publication }
        val binding = PlaceholderPublicationBinding(publication, owner, key.value, ::published)
        publicationBindings += binding
        adapterLookup(publication.adapterId)?.let(binding::attach)
    }

    /**
     * The value for an outside system that wants an answer now. Waits at most [PlaceholderAdapter.WAIT_MILLIS_KEY] milliseconds
     * (the caller says how long in the request values); when the value is late, the last known one is returned
     * and the late result is kept for the next ask.
     */
    private fun published(request: PlaceholderRequest): Any? {
        val staleKey = request.playerId.toString() + request.parameters.toSortedMap()
        val stage = resolve(request)
        stage.thenAccept { value -> if (value != null) stale[staleKey] = value }
        val waitMillis = (request.values[PlaceholderAdapter.WAIT_MILLIS_KEY] as? Long) ?: DEFAULT_WAIT_MILLIS
        val value: Any? = try {
            if (waitMillis <= 0) stage.toCompletableFuture().getNow(null)
            else stage.toCompletableFuture().get(waitMillis, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            null
        } catch (_: ExecutionException) {
            null
        }
        val answer = value ?: stale[staleKey] ?: fallback
        return publishedFormat?.invoke(answer, request) ?: answer
    }

    /** Publishes through [adapter] every publication that asked for it. */
    fun attach(adapter: PlaceholderAdapter) {
        publicationBindings.toList()
            .filter { binding -> binding.adapterId == adapter.id.lowercase(Locale.ROOT) }
            .forEach { binding -> binding.attach(adapter) }
    }

    /** Withdraws every publication that went through the adapter [adapterId]. */
    fun detach(adapterId: String) {
        publicationBindings.toList()
            .filter { binding -> binding.adapterId == adapterId.lowercase(Locale.ROOT) }
            .forEach(PlaceholderPublicationBinding::detach)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return

        disable()
        invalidateCache()
        publicationBindings.toList().forEach { binding -> runCatchingNonFatal(binding::close) }
        publicationBindings.clear()
        closeCallback?.invoke()
        closeCallback = null
    }

    private fun cachedValue(cacheKey: String?): CachedPlaceholderValue<T>? {
        if (cacheKey == null) return null
        return cache[cacheKey]
            ?.takeIf { cached -> !cached.expired(cachePolicy) }
    }

    private fun cacheValue(cacheKey: String?, value: T?) {
        if (cacheKey == null || (value == null && !cachePolicy.cacheNullValues)) return
        synchronized(cache) {
            cache[cacheKey] = CachedPlaceholderValue(value, System.currentTimeMillis())
            while (cache.size > cachePolicy.maximumEntries) {
                cache.remove(cache.keys.first())
            }
        }
    }

    private fun cacheKey(request: PlaceholderRequest): String? = when (cachePolicy.scope) {
        PlaceholderCacheScope.NONE -> null
        PlaceholderCacheScope.GLOBAL -> "global"
        PlaceholderCacheScope.PLUGIN -> request.consumer.value
        PlaceholderCacheScope.PLAYER -> request.playerId?.toString() ?: "no-player"
        PlaceholderCacheScope.ARGUMENTS ->
            request.parameters.toSortedMap().toString() + request.values.toSortedMap().toString()
    }
}

private data class CachedPlaceholderValue<T>(
    val value: T?,
    val writtenAtMillis: Long,
) {
    /** Whether the value is older than the policy allows. */
    fun expired(policy: PlaceholderCachePolicy): Boolean =
        System.currentTimeMillis() - writtenAtMillis >= policy.expireAfterWrite.toMillis()
}

private const val DEFAULT_WAIT_MILLIS = 50L
private const val MAXIMUM_STALE = 256
