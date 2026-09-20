package ru.privatenull.pnlibrary.localization

import ru.privatenull.pnlibrary.common.minecraft.MinecraftVersion
import ru.privatenull.pnlibrary.localization.internal.DefaultMinecraftLocalization
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutorService

interface MinecraftLocalization : AutoCloseable {
    fun availableVersions(): CompletionStage<List<MinecraftVersion>>
    fun availableLocales(version: MinecraftVersion): CompletionStage<List<String>>
    fun load(request: TranslationRequest): CompletionStage<TranslationBundle>
    fun locale(version: MinecraftVersion, locale: String): CompletionStage<LocaleTranslations> =
        load(TranslationRequest.builder().version(version).locale(locale).build()).thenApply { it.locale(locale) }
    fun refresh(request: TranslationRequest): CompletionStage<TranslationBundle>
    override fun close()

    class Builder internal constructor() {
        private var cacheDirectory: Path? = null
        private var manifestTtl = Duration.ofHours(24)
        private var connectTimeout = Duration.ofSeconds(5)
        private var readTimeout = Duration.ofSeconds(15)
        private var memoryEntries = 8
        private var downloadConcurrency = 2
        private var executor: ExecutorService? = null

        fun cacheDirectory(value: Path) = apply { cacheDirectory = value }
        fun manifestTtl(value: Duration) = apply { require(!value.isNegative); manifestTtl = value }
        fun connectTimeout(value: Duration) = apply { require(!value.isNegative && !value.isZero); connectTimeout = value }
        fun readTimeout(value: Duration) = apply { require(!value.isNegative && !value.isZero); readTimeout = value }
        fun memoryEntries(value: Int) = apply { require(value > 0); memoryEntries = value }
        fun downloadConcurrency(value: Int) = apply { require(value > 0); downloadConcurrency = value }
        fun executor(value: ExecutorService) = apply { executor = value }

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

    companion object { @JvmStatic fun builder(): Builder = Builder() }
}
