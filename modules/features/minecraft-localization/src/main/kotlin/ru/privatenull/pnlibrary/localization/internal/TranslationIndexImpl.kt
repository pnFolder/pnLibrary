package ru.privatenull.pnlibrary.localization.internal

import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import ru.privatenull.pnlibrary.localization.TranslationIndex
import ru.privatenull.pnlibrary.localization.TranslationMatch
import java.text.Normalizer
import java.util.Locale
import java.util.Collections

internal class TranslationIndexImpl<T>(matches: List<TranslationMatch<T>>) : TranslationIndex<T> {
    private val entries = matches.map { Indexed(it, normalize(it.translation)) }

    override fun findExact(text: String): List<TranslationMatch<T>> {
        val query = normalize(text)
        return Collections.unmodifiableList(entries.filter { it.normalized == query }.map { it.match })
    }

    override fun search(text: String): List<TranslationMatch<T>> {
        val query = normalize(text)
        if (query.isEmpty()) return emptyList()
        return Collections.unmodifiableList(entries.asSequence().filter { query in it.normalized }
            .sortedWith(compareBy<Indexed<T>>(
                { when { it.normalized == query -> 0; it.normalized.startsWith(query) -> 1; else -> 2 } },
                { it.match.key },
            )).map { it.match }.toList())
    }

    private data class Indexed<T>(val match: TranslationMatch<T>, val normalized: String)

    companion object {
        fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
            .trim().lowercase(Locale.ROOT).replace('ё', 'е').replace(Regex("\\s+"), " ")

        fun keys(values: Map<String, String>) = TranslationIndexImpl(
            values.map { (key, value) -> TranslationMatch(key, value, key) },
        )

        fun materials(values: Map<String, String>) = TranslationIndexImpl(
            values.asSequence().filter { (key, _) -> key.startsWith("item.minecraft.") || key.startsWith("block.minecraft.") }
                .map { (key, value) ->
                    val name = key.substringAfter("minecraft.").uppercase(Locale.ROOT)
                    TranslationMatch(key, value, Material.matchMaterial(name))
                }.toList(),
        )

        @Suppress("DEPRECATION")
        fun enchantments(values: Map<String, String>) = TranslationIndexImpl(
            values.asSequence().filter { (key, _) -> key.startsWith("enchantment.minecraft.") }
                .map { (key, value) ->
                    val name = key.substringAfterLast('.').uppercase(Locale.ROOT)
                    val legacy = ENCHANTMENT_ALIASES[name] ?: name
                    val legacyConstant = Enchantment::class.java.fields.firstOrNull { it.name == legacy }
                        ?.get(null) as? Enchantment
                    val enchantment = legacyConstant ?: Enchantment.values().firstOrNull { enchantment ->
                        enchantment.name.equals(legacy, true) || namespacedKey(enchantment)?.equals(name.lowercase(Locale.ROOT), true) == true
                    }
                    TranslationMatch(key, value, enchantment)
                }.toList(),
        )

        private fun namespacedKey(enchantment: Enchantment): String? = try {
            val key = enchantment.javaClass.methods.firstOrNull { it.name == "getKey" && it.parameterTypes.isEmpty() }
                ?.invoke(enchantment) ?: return null
            key.javaClass.methods.firstOrNull { it.name == "getKey" && it.parameterTypes.isEmpty() }
                ?.invoke(key)?.toString()
        } catch (_: ReflectiveOperationException) {
            null
        }

        private val ENCHANTMENT_ALIASES = mapOf(
            "PROTECTION" to "PROTECTION_ENVIRONMENTAL", "FIRE_PROTECTION" to "PROTECTION_FIRE",
            "FEATHER_FALLING" to "PROTECTION_FALL", "BLAST_PROTECTION" to "PROTECTION_EXPLOSIONS",
            "PROJECTILE_PROTECTION" to "PROTECTION_PROJECTILE", "RESPIRATION" to "OXYGEN",
            "AQUA_AFFINITY" to "WATER_WORKER", "SHARPNESS" to "DAMAGE_ALL",
            "SMITE" to "DAMAGE_UNDEAD", "BANE_OF_ARTHROPODS" to "DAMAGE_ARTHROPODS",
            "LOOTING" to "LOOT_BONUS_MOBS", "EFFICIENCY" to "DIG_SPEED",
            "UNBREAKING" to "DURABILITY", "FORTUNE" to "LOOT_BONUS_BLOCKS",
            "POWER" to "ARROW_DAMAGE", "PUNCH" to "ARROW_KNOCKBACK",
            "FLAME" to "ARROW_FIRE", "INFINITY" to "ARROW_INFINITE",
        )
    }
}
