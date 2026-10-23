package org.pnfolder.pnlibrary.localization.internal

import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion
import org.pnfolder.pnlibrary.localization.LocaleTranslations
import org.pnfolder.pnlibrary.localization.MinecraftLocalization
import org.pnfolder.pnlibrary.localization.TranslationBundle
import org.pnfolder.pnlibrary.localization.TranslationException
import org.pnfolder.pnlibrary.localization.TranslationIndex
import org.pnfolder.pnlibrary.localization.TranslationMetadata
import org.pnfolder.pnlibrary.localization.TranslationRequest
import org.pnfolder.pnlibrary.localization.TranslationSource
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayouts
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Collections
import java.util.LinkedHashMap
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean

internal class DefaultMinecraftLocalization internal constructor(
    cacheDirectory: Path,
    private val manifestTtl: Duration,
    connectTimeout: Duration,
    readTimeout: Duration,
    memoryEntries: Int,
    downloadConcurrency: Int,
    suppliedExecutor: ExecutorService?,
    httpClient: LocalizationHttpClient? = null,
    override val layouts: KeyboardLayouts = KeyboardLayouts.create(),
) : MinecraftLocalization {
    private val ownsExecutor = suppliedExecutor == null
    private val executor = suppliedExecutor ?: Executors.newFixedThreadPool(downloadConcurrency) { task ->
        Thread(task, "pnlibrary-localization").apply { isDaemon = true }
    }
    private val store = VerifiedFileStore(cacheDirectory.resolve("minecraft"))
    private val resolver = MojangAssetResolver(httpClient ?: LocalizationHttpClient(connectTimeout, readTimeout), store)
    private val permits = Semaphore(downloadConcurrency)
    private val closed = AtomicBoolean(false)
    private val lifecycle = Any()
    private val pending = HashSet<CompletableFuture<*>>()
    private val inFlight = ConcurrentHashMap<CacheKey, CompletableFuture<LoadedLocale>>()
    private val memory = object : LinkedHashMap<CacheKey, LoadedLocale>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, LoadedLocale>?) = size > memoryEntries
    }

    override fun availableVersions(): CompletionStage<List<MinecraftVersion>> = async {
        val manifest = manifest(allowStale = true)
        if (manifest == null) MinecraftVersion.supported() else resolver.versions(manifest)
    }

    override fun availableLocales(version: MinecraftVersion): CompletionStage<List<String>> = async {
        requireKnown(version)
        resolver.locales(version, manifest(allowStale = false) ?: throw offline())
    }

    override fun load(request: TranslationRequest): CompletionStage<TranslationBundle> = load(request, refresh = false)
    override fun refresh(request: TranslationRequest): CompletionStage<TranslationBundle> = load(request, refresh = true)

    private fun load(request: TranslationRequest, refresh: Boolean): CompletionStage<TranslationBundle> {
        ensureOpen()
        val requested = LinkedHashSet(request.locales)
        request.fallbackLocale?.let(requested::add)
        val stages = requested.associateWith { loadLocale(request.version, it, refresh) }
        return CompletableFuture.allOf(*stages.values.toTypedArray()).thenApply {
            val loaded = stages.mapValues { (_, future) -> future.join() }
            DefaultTranslationBundle(request.version, request.locales, request.fallbackLocale, loaded)
        }
    }

    private fun loadLocale(version: MinecraftVersion, locale: String, refresh: Boolean): CompletableFuture<LoadedLocale> {
        requireKnown(version)
        val normalized = try { TranslationRequest.normalizeLocale(locale) } catch (error: IllegalArgumentException) {
            return failed(TranslationException(TranslationException.Reason.INVALID_LOCALE, error.message ?: "Invalid locale", error))
        }
        val key = CacheKey(version, normalized)
        if (!refresh) synchronized(memory) { memory[key] }?.let {
            return CompletableFuture.completedFuture(it.withSource(TranslationSource.MEMORY))
        }
        inFlight[key]?.let { return it }
        val promise = CompletableFuture<LoadedLocale>()
        val existing = inFlight.putIfAbsent(key, promise)
        if (existing != null) return existing
        return submit(promise, beforeCompletion = { inFlight.remove(key, promise) }) {
            permits.acquire()
            try { loadLocaleNow(key, refresh) } finally { permits.release() }
        }
    }

    private fun loadLocaleNow(key: CacheKey, refresh: Boolean): LoadedLocale {
        val path = store.translation(key.version.text, key.locale)
        if (!refresh) store.read(path, MAX_LANGUAGE_BYTES)?.let { bytes ->
            try {
                return cache(key, bytes, TranslationMetadata(TranslationSource.DISK, store.sha1(bytes), false))
            } catch (_: TranslationException) {
                store.quarantine(path)
            }
        }
        return try {
            val manifest = manifest(allowStale = false) ?: throw offline()
            val resolved = resolver.language(key.version, key.locale, manifest)
            val loaded = cache(key, resolved.bytes, TranslationMetadata(TranslationSource.NETWORK, resolved.hash, false))
            store.write(path, resolved.bytes)
            loaded
        } catch (error: TranslationException) {
            if (refresh) store.read(path, MAX_LANGUAGE_BYTES)?.let { bytes ->
                return cache(key, bytes, TranslationMetadata(TranslationSource.STALE_DISK, store.sha1(bytes), true))
            }
            throw error
        }
    }

    private fun cache(key: CacheKey, bytes: ByteArray, metadata: TranslationMetadata): LoadedLocale {
        val loaded = LoadedLocale(key.locale, TranslationJsonCodec.parse(bytes), metadata, layouts)
        synchronized(memory) { if (!closed.get()) memory[key] = loaded }
        return loaded
    }

    @Synchronized
    private fun manifest(allowStale: Boolean): ByteArray? {
        val path = store.manifest()
        val cached = store.read(path, MAX_MANIFEST_BYTES)
        val fresh = cached != null && Duration.ofMillis(System.currentTimeMillis() - Files.getLastModifiedTime(path).toMillis()) <= manifestTtl
        if (fresh) return cached
        return try {
            val bytes = resolver.manifestBytes()
            store.write(path, bytes)
            bytes
        } catch (error: TranslationException) {
            if (allowStale) cached else throw error
        }
    }

    private fun <T> async(action: () -> T): CompletableFuture<T> {
        ensureOpen()
        return submit(CompletableFuture(), action = action)
    }

    private fun <T> submit(
        promise: CompletableFuture<T>,
        beforeCompletion: () -> Unit = {},
        action: () -> T,
    ): CompletableFuture<T> {
        val accepted = synchronized(lifecycle) {
            if (closed.get()) false else { pending.add(promise); true }
        }
        if (!accepted) {
            beforeCompletion()
            promise.completeExceptionally(closedError())
            return promise
        }
        promise.whenComplete { _, _ -> synchronized(lifecycle) { pending.remove(promise) } }
        try {
            executor.execute {
                if (!promise.isDone) {
                    try {
                        ensureOpen()
                        val result = action()
                        // Release download ownership before waking callers, including refresh requests.
                        beforeCompletion()
                        promise.complete(result)
                    } catch (error: Throwable) {
                        beforeCompletion()
                        promise.completeExceptionally(error)
                    }
                }
            }
        } catch (error: RejectedExecutionException) {
            beforeCompletion()
            promise.completeExceptionally(error)
        }
        return promise
    }

    private fun ensureOpen() {
        if (closed.get()) throw closedError()
    }

    private fun requireKnown(version: MinecraftVersion) {
        if (!version.known) throw TranslationException(
            TranslationException.Reason.UNSUPPORTED_VERSION, "UNKNOWN Minecraft version is not supported",
        )
    }

    override fun close() {
        val abandoned = synchronized(lifecycle) {
            if (!closed.compareAndSet(false, true)) return
            pending.toList().also { pending.clear() }
        }
        inFlight.clear()
        abandoned.forEach { it.completeExceptionally(closedError()) }
        synchronized(memory) { memory.clear() }
        if (ownsExecutor) executor.shutdownNow()
    }

    private fun closedError() = TranslationException(TranslationException.Reason.CLOSED, "Minecraft localization service is closed")
    private fun offline() = TranslationException(TranslationException.Reason.OFFLINE, "No cached Minecraft manifest is available")
    private fun <T> failed(error: Throwable): CompletableFuture<T> = CompletableFuture<T>().also { it.completeExceptionally(error) }
    private data class CacheKey(val version: MinecraftVersion, val locale: String)

    private companion object {
        const val MAX_LANGUAGE_BYTES = 16 * 1024 * 1024
        const val MAX_MANIFEST_BYTES = 4 * 1024 * 1024
    }
}

internal class LoadedLocale private constructor(
    override val locale: String,
    private val data: LocaleData,
    override val metadata: TranslationMetadata,
    override val layouts: KeyboardLayouts,
) : LocaleTranslations {
    constructor(locale: String, values: Map<String, String>, metadata: TranslationMetadata, layouts: KeyboardLayouts) :
        this(locale, LocaleData(values, layouts), metadata, layouts)
    override fun translate(key: String): Optional<String> = Optional.ofNullable(data.values[key])
    override fun translations(): Map<String, String> = data.values
    override fun keys(): TranslationIndex<String> = data.keys
    fun withSource(source: TranslationSource) = LoadedLocale(locale, data, metadata.copy(source = source), layouts)

    private class LocaleData(values: Map<String, String>, layouts: KeyboardLayouts) {
        val values = Collections.unmodifiableMap(LinkedHashMap(values))
        val keys by lazy { TranslationIndexImpl.keys(this.values, layouts) }
    }
}

private class DefaultTranslationBundle(
    override val version: MinecraftVersion,
    requested: Set<String>,
    private val fallback: String?,
    private val loaded: Map<String, LoadedLocale>,
) : TranslationBundle {
    override val locales: Set<String> = Collections.unmodifiableSet(LinkedHashSet(requested))
    override fun locale(id: String): LocaleTranslations {
        val normalized = TranslationRequest.normalizeLocale(id)
        val primary = loaded[normalized] ?: throw IllegalArgumentException("Locale $normalized was not requested")
        val fallbackLocale = fallback?.let(loaded::get)
        return if (fallbackLocale == null || fallbackLocale === primary) primary else FallbackLocale(primary, fallbackLocale)
    }
}

private class FallbackLocale(
    private val primary: LoadedLocale,
    private val fallback: LoadedLocale,
) : LocaleTranslations by primary {
    override fun translate(key: String): Optional<String> {
        val value = primary.translate(key)
        return if (value.isPresent) value else fallback.translate(key)
    }
}
