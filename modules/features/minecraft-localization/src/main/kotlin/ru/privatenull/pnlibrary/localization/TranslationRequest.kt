package ru.privatenull.pnlibrary.localization

import ru.privatenull.pnlibrary.common.minecraft.MinecraftVersion
import java.util.Locale
import java.util.Collections

/** Immutable request for exact Minecraft version/locale translation tables. */
class TranslationRequest internal constructor(
    val version: MinecraftVersion,
    val locales: Set<String>,
    val fallbackLocale: String?,
) {
    class Builder internal constructor() {
        private var version: MinecraftVersion? = null
        private val locales = linkedSetOf<String>()
        private var fallbackLocale: String? = null

        fun version(value: MinecraftVersion) = apply { version = value }
        fun locale(value: String) = apply { locales += normalizeLocale(value) }
        fun locales(vararg values: String) = apply { values.forEach(::locale) }
        fun locales(values: Collection<String>) = apply { values.forEach(::locale) }
        fun fallback(value: String?) = apply { fallbackLocale = value?.let(::normalizeLocale) }

        fun build(): TranslationRequest {
            val selectedVersion = requireNotNull(version) { "Minecraft version is required" }
            require(selectedVersion.known) { "UNKNOWN Minecraft version is not supported" }
            require(locales.isNotEmpty()) { "At least one locale is required" }
            return TranslationRequest(selectedVersion, Collections.unmodifiableSet(LinkedHashSet(locales)), fallbackLocale)
        }
    }

    companion object {
        @JvmStatic fun builder(): Builder = Builder()

        internal fun normalizeLocale(value: String): String {
            val normalized = value.trim().lowercase(Locale.ROOT).replace('-', '_')
            require(LOCALE.matches(normalized)) { "Invalid Minecraft locale: $value" }
            return normalized
        }

        private val LOCALE = Regex("[a-z0-9]+(?:_[a-z0-9]+)*")
    }
}
