package org.pnfolder.pnlibrary.bukkit.localization.internal

import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.bukkit.localization.BukkitLocaleTranslations
import org.pnfolder.pnlibrary.localization.LocaleTranslations
import org.pnfolder.pnlibrary.localization.TranslationMetadata
import org.pnfolder.pnlibrary.localization.TranslationSource
import java.util.Optional

private class FakeLocaleTranslations(private val values: Map<String, String>) : LocaleTranslations {
    override val locale = "ru"
    override val metadata = TranslationMetadata(TranslationSource.MEMORY, null, false)
    override fun translate(key: String): Optional<String> = Optional.ofNullable(values[key])
    override fun translations(): Map<String, String> = values
    override fun keys() = error("not needed for this test")
}

class TranslationIndexTest {
    @Test
    fun `search normalizes russian spelling and ranks exact before prefix`() {
        val index = BukkitLocaleTranslations.wrap(
            FakeLocaleTranslations(
                linkedMapOf(
                    "item.minecraft.stone" to "Камень",
                    "item.minecraft.stone_sword" to "Каменный меч",
                    "item.minecraft.diamond_sword" to "Алмазный меч",
                ),
            ),
        ).materials()
        assertEquals(Material.STONE, index.findExact("  КАМЕНЬ ").single().value)
        assertEquals(listOf("item.minecraft.stone", "item.minecraft.stone_sword"), index.search("камен").map { it.key })
    }

    @Suppress("DEPRECATION")
    @Test
    fun `modern enchantment keys map to legacy Bukkit names`() {
        val index = BukkitLocaleTranslations.wrap(
            FakeLocaleTranslations(mapOf("enchantment.minecraft.sharpness" to "Острота")),
        ).enchantments()
        assertEquals(Enchantment.DAMAGE_ALL, index.findExact("Острота").single().value)
    }
}
