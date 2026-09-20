package ru.privatenull.pnlibrary.localization

import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import java.util.Optional

interface LocaleTranslations {
    val locale: String
    val metadata: TranslationMetadata
    fun translate(key: String): Optional<String>
    fun translateOrKey(key: String): String = translate(key).orElse(key)
    fun translations(): Map<String, String>
    fun keys(): TranslationIndex<String>
    fun materials(): TranslationIndex<Material>
    fun enchantments(): TranslationIndex<Enchantment>
}
