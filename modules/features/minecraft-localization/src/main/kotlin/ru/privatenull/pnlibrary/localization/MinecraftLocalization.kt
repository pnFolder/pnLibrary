package ru.privatenull.pnlibrary.localization

import ru.privatenull.pnlibrary.common.minecraft.MinecraftVersion
import ru.privatenull.pnlibrary.localization.internal.DefaultMinecraftLocalization
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutorService

/**
 * Loads and caches Mojang translation data for explicit Minecraft versions.
 *
 * Network and disk operations complete asynchronously. Closing the service
 * releases owned executors and rejects new requests.
 */
interface MinecraftLocalization : AutoCloseable {
    /** Returns known release versions in descending order. */
    fun availableVersions(): CompletionStage<List<MinecraftVersion>>

    /** Returns locale identifiers available for [version]. */
    fun availableLocales(version: MinecraftVersion): CompletionStage<List<String>>

    /** Loads every locale declared by [request], using valid cached data when possible. */
    fun load(request: TranslationRequest): CompletionStage<TranslationBundle>

    /** Loads one [locale] for [version]. */
    fun locale(version: MinecraftVersion, locale: String): CompletionStage<LocaleTranslations> =
        load(TranslationRequest.builder().version(version).locale(locale).build()).thenApply { it.locale(locale) }

    /** Forces validation and refresh of the translations declared by [request]. */
    fun refresh(request: TranslationRequest): CompletionStage<TranslationBundle>

    /** Releases resources owned by this localization service. */
    override fun close()

    /** Configures filesystem, timeout, cache, and execution limits. */
    class Builder internal constructor() {
        private var cacheDirectory: Path? = null
        private var manifestTtl = Duration.ofHours(24)
        private var connectTimeout = Duration.ofSeconds(5)
        private var readTimeout = Duration.ofSeconds(15)
        private var memoryEntries = 8
        private var downloadConcurrency = 2
        private var executor: ExecutorService? = null

        /** Sets the directory used for manifests, indexes, and locale caches. */
        fun cacheDirectory(value: Path) = apply {
            cacheDirectory = value
        }

        /** Sets how long version metadata may be reused without revalidation. */
        fun manifestTtl(value: Duration) = apply {
            require(!value.isNegative)
            manifestTtl = value
        }

        /** Sets the HTTP connection timeout. */
        fun connectTimeout(value: Duration) = apply {
            require(!value.isNegative && !value.isZero)
            connectTimeout = value
        }

        /** Sets the HTTP response read timeout. */
        fun readTimeout(value: Duration) = apply {
            require(!value.isNegative && !value.isZero)
            readTimeout = value
        }

        /** Sets the maximum number of locale bundles retained in memory. */
        fun memoryEntries(value: Int) = apply {
            require(value > 0)
            memoryEntries = value
        }

        /** Sets the maximum number of concurrent network downloads. */
        fun downloadConcurrency(value: Int) = apply {
            require(value > 0)
            downloadConcurrency = value
        }

        /** Supplies an externally owned executor; the service will not shut it down. */
        fun executor(value: ExecutorService) = apply {
            executor = value
        }

        /** Validates the settings and creates a localization service. */
        fun build(): MinecraftLocalization = DefaultMinecraftLocalization(
            cacheDirectory = requireNotNull(cacheDirectory) { "cacheDirectory is required" },
            manifestTtl = manifestTtl,
            connectTimeout = connectTimeout,
            readTimeout = readTimeout,
            memoryEntries = memoryEntries,
            downloadConcurrency = downloadConcurrency,
            suppliedExecutor = executor,
        )
    }

    /** Creates localization-service builders. */
    companion object {
        /** Returns an empty localization-service builder. */
        @JvmStatic
        fun builder(): Builder = Builder()
    }
}
