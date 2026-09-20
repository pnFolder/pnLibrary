package ru.privatenull.pnlibrary.localization.internal

import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import ru.privatenull.pnlibrary.localization.TranslationIndex
import ru.privatenull.pnlibrary.localization.TranslationMatch
import java.text.Normalizer
import java.util.Locale

internal class TranslationIndexImpl<T>(matches: List<TranslationMatch<T>>) : TranslationIndex<T> {
    private val entries = matches.map { Indexed(it, normalize(it.translation)) }

    override fun findExact(text: String): List<TranslationMatch<T>> {
        val query = normalize(text)
        return entries.filter { it.normalized == query }.map { it.match }
    }

    override fun search(text: String): List<TranslationMatch<T>> {
        val query = normalize(text)
        if (query.isEmpty()) return emptyList()
        return entries.asSequence().filter { query in it.normalized }
            .sortedWith(compareBy<Indexed<T>>(
                { when { it.normalized == query -> 0; it.normalized.startsWith(query) -> 1; else -> 2 } },
                { it.match.key },
            )).map { it.match }.toList()
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
                    val enchantment = Enchantment.values().firstOrNull {
                        it.name.equals(name, true) || it.name.replace("_", "").equals(name.replace("_", ""), true)
                    }
                    TranslationMatch(key, value, enchantment)
                }.toList(),
        )
    }
}
