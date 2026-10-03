package ru.privatenull.pnlibrary.core.placeholders

import ru.privatenull.pnlibrary.api.placeholders.ExternalPlaceholderRegistration
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderAccess
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderAdapter
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderCachePolicy
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderCacheScope
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderKey
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderPublication
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderRegistration
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderRequest
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderUpdater
import ru.privatenull.pnlibrary.api.plugin.PluginId
import java.util.Collections
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicBoolean

/** One registered placeholder, including its cache, update policy and external publications. */
internal class PlaceholderEntry<T : Any>(
    override val owner: PluginId,
    override val key: PlaceholderKey<T>,
    private val resolver: (PlaceholderRequest) -> CompletionStage<T?>,
    private val updater: PlaceholderUpdater<T>?,
    val access: PlaceholderAccess,
    private val updateAccess: PlaceholderAccess,
    private val cachePolicy: PlaceholderCachePolicy,
    private val fallback: T?,
    private val adapterLookup: (String) -> PlaceholderAdapter?,
) : PlaceholderRegistration<T> {
    private val enabled = AtomicBoolean(true)
    private val closed = AtomicBoolean(false)
    private val cache = Collections.synchronizedMap(LinkedHashMap<String, CachedPlaceholderValue<T>>())
    private val publicationBindings = Collections.synchronizedList(mutableListOf<PlaceholderPublicationBinding>())

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

    fun whenClosed(callback: () -> Unit) {
        closeCallback = callback
    }

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

    fun update(consumer: PluginId, request: PlaceholderRequest, value: String): T? {
        check(updateAccess.allows(owner, consumer)) {
            "Plugin $consumer cannot update ${owner.value}:${key.value}"
        }
        val operation = updater ?: error("Placeholder ${owner.value}:${key.value} is read-only")
        return operation.update(request, value).also { invalidateCache() }
    }

    fun publish(publication: PlaceholderPublication) {
        val binding = PlaceholderPublicationBinding(publication, owner, key.value) { request ->
            @Suppress("UNCHECKED_CAST")
            resolve(request).toCompletableFuture().join() as Any?
        }
        publicationBindings += binding
        adapterLookup(publication.adapterId)?.let(binding::attach)
    }

    fun attach(adapter: PlaceholderAdapter) {
        publicationBindings.toList()
            .filter { binding -> binding.adapterId == adapter.id.lowercase(Locale.ROOT) }
            .forEach { binding -> binding.attach(adapter) }
    }

    fun detach(adapterId: String) {
        publicationBindings.toList()
            .filter { binding -> binding.adapterId == adapterId.lowercase(Locale.ROOT) }
            .forEach(PlaceholderPublicationBinding::detach)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return

        disable()
        invalidateCache()
        publicationBindings.toList().forEach { binding -> runCatching(binding::close) }
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
    fun expired(policy: PlaceholderCachePolicy): Boolean =
        System.currentTimeMillis() - writtenAtMillis >= policy.expireAfterWriteMillis
}
