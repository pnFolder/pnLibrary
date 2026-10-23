package org.pnfolder.pnlibrary.localization

import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion
import java.util.Collections
import java.util.Locale

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
        fun version(value: MinecraftVersion): Builder = apply { version = value }
        /** Adds one locale after normalization. */
        fun locale(value: String): Builder = apply { locales += normalizeLocale(value) }
        /** Adds one typed [locale]. */
        fun locale(locale: MinecraftLocale): Builder = locale(locale.code)
        /** Adds all supplied locale identifiers. */
        fun locales(vararg values: String): Builder = apply { values.forEach(::locale) }
        /** Adds all supplied typed locales in lookup order. */
        fun locales(vararg locales: MinecraftLocale): Builder = apply { locales.forEach { locale(it.code) } }
        /** Adds all locale identifiers in [values]. */
        fun locales(values: Collection<String>): Builder = apply { values.forEach(::locale) }
        /** Sets the optional fallback locale after normalization. */
        fun fallback(value: String?): Builder = apply { fallbackLocale = value?.let(::normalizeLocale) }
        /** Sets the optional fallback to a typed [locale]. */
        fun fallback(locale: MinecraftLocale): Builder = fallback(locale.code)

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
