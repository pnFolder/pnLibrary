package ru.privatenull.pnlibrary.localization

import ru.privatenull.pnlibrary.common.minecraft.MinecraftVersion
import java.util.Locale
import java.util.Collections

/**
 * Immutable request for exact Minecraft version/locale translation tables.
 *
 * @property version known Minecraft version to load
 * @property locales normalized locale identifiers to load
 * @property fallbackLocale optional normalized fallback locale
 */
class TranslationRequest internal constructor(
    val version: MinecraftVersion,
    val locales: Set<String>,
    val fallbackLocale: String?,
) {
    /** Fluent Java-friendly builder for [TranslationRequest]. */
    class Builder internal constructor() {
        private var version: MinecraftVersion? = null
        private val locales = linkedSetOf<String>()
        private var fallbackLocale: String? = null

        /** Selects the known Minecraft [value]. */
        fun version(value: MinecraftVersion) = apply { version = value }
        /** Adds one locale after normalization. */
        fun locale(value: String) = apply { locales += normalizeLocale(value) }
        /** Adds all supplied locale identifiers. */
        fun locales(vararg values: String) = apply { values.forEach(::locale) }
        /** Adds all locale identifiers in [values]. */
        fun locales(values: Collection<String>) = apply { values.forEach(::locale) }
        /** Sets the optional fallback locale after normalization. */
        fun fallback(value: String?) = apply { fallbackLocale = value?.let(::normalizeLocale) }

        /** Validates and creates the immutable request. */
        fun build(): TranslationRequest {
            val selectedVersion = requireNotNull(version) { "Minecraft version is required" }
            require(selectedVersion.known) { "UNKNOWN Minecraft version is not supported" }
            require(locales.isNotEmpty()) { "At least one locale is required" }
            return TranslationRequest(selectedVersion, Collections.unmodifiableSet(LinkedHashSet(locales)), fallbackLocale)
        }
    }

    /** Creates translation requests. */
    companion object {
        /** Returns an empty translation-request builder. */
        @JvmStatic
        fun builder(): Builder = Builder()

        internal fun normalizeLocale(value: String): String {
            val normalized = value.trim().lowercase(Locale.ROOT).replace('-', '_')
            require(LOCALE.matches(normalized)) { "Invalid Minecraft locale: $value" }
            return normalized
        }

        private val LOCALE = Regex("[a-z0-9]+(?:_[a-z0-9]+)*")
    }
}
