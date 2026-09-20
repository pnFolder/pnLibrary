package ru.privatenull.pnlibrary.localization.internal

import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import ru.privatenull.pnlibrary.common.minecraft.MinecraftVersion
import ru.privatenull.pnlibrary.localization.*
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.Collections
import java.util.LinkedHashMap
import java.util.Optional
import java.util.concurrent.*
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
) : MinecraftLocalization {
    private val ownsExecutor = suppliedExecutor == null
    private val executor = suppliedExecutor ?: Executors.newFixedThreadPool(downloadConcurrency) { task ->
        Thread(task, "pnlibrary-localization").apply { isDaemon = true }
    }
    private val store = VerifiedFileStore(cacheDirectory.resolve("minecraft"))
    private val resolver = MojangAssetResolver(httpClient ?: LocalizationHttpClient(connectTimeout, readTimeout), store)
    private val permits = Semaphore(downloadConcurrency)
    private val closed = AtomicBoolean(false)
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
        executor.execute {
            try {
                permits.acquire()
                try { promise.complete(loadLocaleNow(key, refresh)) } finally { permits.release() }
            } catch (error: Throwable) {
                promise.completeExceptionally(error)
            } finally {
                inFlight.remove(key, promise)
            }
        }
        return promise
    }

    private fun loadLocaleNow(key: CacheKey, refresh: Boolean): LoadedLocale {
        val path = store.translation(key.version.text, key.locale)
        if (!refresh) store.read(path)?.let { bytes ->
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
            if (refresh) store.read(path)?.let { bytes ->
                return cache(key, bytes, TranslationMetadata(TranslationSource.STALE_DISK, store.sha1(bytes), true))
            }
            throw error
        }
    }

    private fun cache(key: CacheKey, bytes: ByteArray, metadata: TranslationMetadata): LoadedLocale {
        val loaded = LoadedLocale(key.locale, TranslationJsonCodec.parse(bytes), metadata)
        synchronized(memory) { memory[key] = loaded }
        return loaded
    }

    private fun manifest(allowStale: Boolean): ByteArray? {
        val path = store.manifest()
        val cached = store.read(path)
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
        return CompletableFuture.supplyAsync({ action() }, executor)
    }

    private fun ensureOpen() {
        if (closed.get()) throw TranslationException(TranslationException.Reason.CLOSED, "Minecraft localization service is closed")
    }

    private fun requireKnown(version: MinecraftVersion) {
        if (!version.known) throw TranslationException(
            TranslationException.Reason.UNSUPPORTED_VERSION, "UNKNOWN Minecraft version is not supported",
        )
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        inFlight.values.forEach { it.cancel(true) }
        synchronized(memory) { memory.clear() }
        if (ownsExecutor) executor.shutdownNow()
    }

    private fun offline() = TranslationException(TranslationException.Reason.OFFLINE, "No cached Minecraft manifest is available")
    private fun <T> failed(error: Throwable): CompletableFuture<T> = CompletableFuture<T>().also { it.completeExceptionally(error) }
    private data class CacheKey(val version: MinecraftVersion, val locale: String)
}

internal class LoadedLocale(
    override val locale: String,
    values: Map<String, String>,
    override val metadata: TranslationMetadata,
) : LocaleTranslations {
    private val values = Collections.unmodifiableMap(LinkedHashMap(values))
    private val keys = TranslationIndexImpl.keys(values)
    private val materials = TranslationIndexImpl.materials(values)
    private val enchantments = TranslationIndexImpl.enchantments(values)
    override fun translate(key: String): Optional<String> = Optional.ofNullable(values[key])
    override fun translations(): Map<String, String> = values
    override fun keys(): TranslationIndex<String> = keys
    override fun materials(): TranslationIndex<Material> = materials
    override fun enchantments(): TranslationIndex<Enchantment> = enchantments
    fun withSource(source: TranslationSource) = LoadedLocale(locale, values, metadata.copy(source = source))
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
