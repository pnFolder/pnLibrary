package org.pnfolder.pnlibrary.localization

import java.util.Optional

/**
 * Parsed Minecraft translation table for one locale and game version.
 *
 * Platform-specific reverse indexes (e.g. Bukkit materials/enchantments) are
 * provided by platform modules on top of this neutral contract.
 */
interface LocaleTranslations {
    /** Normalized locale identifier. */
    val locale: String
    /** Provenance and integrity metadata for this table. */
    val metadata: TranslationMetadata
    /** Returns the translation for [key], when present. */
    fun translate(key: String): Optional<String>
    /** Returns the translation for [key], or [key] itself when absent. */
    fun translateOrKey(key: String): String = translate(key).orElse(key)
    /**
     * Translates a typed platform [value] through its [type].
     *
     * Never returns null: when no candidate key is present the result carries a readable fallback with
     * [Translation.localized] set to `false`.
     */
    fun <T> translate(type: TranslatableType<T>, value: T): Translation {
        val keys = type.keysOf(value)
        for (key in keys) {
            val text = translate(key)
            if (text.isPresent) return Translation(key, text.get(), true)
        }
        return Translation(keys.firstOrNull(), type.fallbackName(value), false)
    }
    /** Returns an immutable key-to-text translation map. */
    fun translations(): Map<String, String>
    /** Returns a reverse-search index of raw translation keys. */
    fun keys(): TranslationIndex<String>
}
