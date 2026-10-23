package org.pnfolder.pnlibrary.localization

import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion
import org.pnfolder.pnlibrary.localization.internal.DefaultMinecraftLocalization
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

    /** Loads one typed [locale] for [version]. */
    fun locale(version: MinecraftVersion, locale: MinecraftLocale): CompletionStage<LocaleTranslations> =
        locale(version, locale.code)

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
        fun cacheDirectory(value: Path): Builder = apply {
            cacheDirectory = value
        }

        /** Sets how long version metadata may be reused without revalidation. */
        fun manifestTtl(value: Duration): Builder = apply {
            require(!value.isNegative) { "Manifest TTL must not be negative" }
            manifestTtl = value
        }

        /** Sets the HTTP connection timeout. */
        fun connectTimeout(value: Duration): Builder = apply {
            require(!value.isNegative && !value.isZero) { "Connect timeout must be positive" }
            connectTimeout = value
        }

        /** Sets the HTTP response read timeout. */
        fun readTimeout(value: Duration): Builder = apply {
            require(!value.isNegative && !value.isZero) { "Read timeout must be positive" }
            readTimeout = value
        }

        /** Sets the maximum number of locale bundles retained in memory. */
        fun memoryEntries(value: Int): Builder = apply {
            require(value > 0) { "Memory entries must be positive" }
            memoryEntries = value
        }

        /** Sets the maximum number of concurrent network downloads. */
        fun downloadConcurrency(value: Int): Builder = apply {
            require(value > 0) { "Download concurrency must be positive" }
            downloadConcurrency = value
        }

        /** Supplies an externally owned executor; the service will not shut it down. */
        fun executor(value: ExecutorService): Builder = apply {
            executor = value
        }

        /** Validates the settings and creates a localization service. */
        fun build(): MinecraftLocalization = DefaultMinecraftLocalization(
            cacheDirectory = requireNotNull(cacheDirectory) { "Cache directory is required" },
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
