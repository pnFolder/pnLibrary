package ru.privatenull.pnlibrary.localization

import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import java.util.Optional

/** Parsed Minecraft translation table for one locale and game version. */
interface LocaleTranslations {
    /** Normalized locale identifier. */
    val locale: String
    /** Provenance and integrity metadata for this table. */
    val metadata: TranslationMetadata
    /** Returns the translation for [key], when present. */
    fun translate(key: String): Optional<String>
    /** Returns the translation for [key], or [key] itself when absent. */
    fun translateOrKey(key: String): String = translate(key).orElse(key)
    /** Returns an immutable key-to-text translation map. */
    fun translations(): Map<String, String>
    /** Returns a reverse-search index of raw translation keys. */
    fun keys(): TranslationIndex<String>
    /** Returns a reverse-search index of Bukkit materials. */
    fun materials(): TranslationIndex<Material>
    /** Returns a reverse-search index of Bukkit enchantments. */
    fun enchantments(): TranslationIndex<Enchantment>
}
