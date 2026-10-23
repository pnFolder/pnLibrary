package org.pnfolder.pnlibrary.localization

/**
 * A typed category of platform values (materials, enchantments, biomes, ...) that resolve to Minecraft
 * translation keys.
 *
 * Platform modules implement one per value type so any typed object translates through the same
 * [LocaleTranslations.translate] entry point, without adding a method per type to the contract.
 */
interface TranslatableType<T> {
    /** Candidate translation keys for [value], in priority order; the first key present in the table wins. */
    fun keysOf(value: T): List<String>

    /** Last-resort human-readable name used when none of [keysOf] is present in the table. */
    fun fallbackName(value: T): String
}
