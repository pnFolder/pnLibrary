package org.pnfolder.pnlibrary.bukkit.localization

import org.bukkit.DyeColor
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.block.Biome
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.EntityType
import org.bukkit.potion.PotionEffectType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.localization.LocaleTranslations
import org.pnfolder.pnlibrary.localization.TranslationMetadata
import org.pnfolder.pnlibrary.localization.TranslationSource
import java.util.Optional

private class FakeLocaleTranslations(private val values: Map<String, String>) : LocaleTranslations {
    override val locale = "ru_ru"
    override val metadata = TranslationMetadata(TranslationSource.MEMORY, null, false)
    override fun translate(key: String): Optional<String> = Optional.ofNullable(values[key])
    override fun translations(): Map<String, String> = values
    override fun keys() = error("not needed for this test")
}

class ForwardLookupTest {
    private val translations = BukkitLocaleTranslations.wrap(
        FakeLocaleTranslations(
            linkedMapOf(
                "block.minecraft.stone" to "Камень",
                "item.minecraft.diamond" to "Алмаз",
                "enchantment.minecraft.sharpness" to "Острота",
                "entity.minecraft.zombie" to "Зомби",
                "effect.minecraft.haste" to "Спешка",
                "biome.minecraft.plains" to "Равнина",
                "color.minecraft.red" to "Красный",
                "gameMode.survival" to "Выживание",
            ),
        ),
    )

    // A pre-1.13 table carries the old key scheme; the same calls must still resolve.
    private val legacyTranslations = BukkitLocaleTranslations.wrap(
        FakeLocaleTranslations(
            linkedMapOf(
                "enchantment.damage.all" to "Острота",
                "effect.digSpeed" to "Спешка",
                "entity.Zombie.name" to "Зомби",
            ),
        ),
    )

    @Test
    fun `translate resolves a material directly`() {
        val stone = translations.translate(Material.STONE)
        assertEquals("Камень", stone.text)
        assertTrue(stone.localized)
        assertEquals("Алмаз", translations.translate(Material.DIAMOND).text)
    }

    @Test
    fun `translate falls back to a readable form when absent`() {
        val bedrock = translations.translate(Material.BEDROCK)
        assertEquals("bedrock", bedrock.text)
        assertFalse(bedrock.localized)
    }

    @Test
    @Suppress("DEPRECATION")
    fun `translate resolves an enchantment by modern key`() {
        val sharpness = translations.translate(Enchantment.DAMAGE_ALL)
        assertEquals("Острота", sharpness.text)
        assertTrue(sharpness.localized)
    }

    @Test
    fun `translate resolves an entity type`() {
        val zombie = translations.translate(EntityType.ZOMBIE)
        assertEquals("Зомби", zombie.text)
        assertTrue(zombie.localized)
    }

    @Test
    fun `translate resolves an effect by modern key despite the legacy Bukkit name`() {
        val haste = translations.translate(PotionEffectType.FAST_DIGGING)
        assertEquals("Спешка", haste.text)
        assertTrue(haste.localized)
    }

    @Test
    fun `translate resolves a biome`() {
        val plains = translations.translate(Biome.PLAINS)
        assertEquals("Равнина", plains.text)
        assertTrue(plains.localized)
    }

    @Test
    fun `translate resolves a dye color`() {
        val red = translations.translate(DyeColor.RED)
        assertEquals("Красный", red.text)
        assertTrue(red.localized)
        assertEquals(DyeColor.RED, translations.colors().findExact("Красный").single().value)
    }

    @Test
    fun `translate resolves a game mode`() {
        val survival = translations.translate(GameMode.SURVIVAL)
        assertEquals("Выживание", survival.text)
        assertTrue(survival.localized)
        assertEquals(GameMode.SURVIVAL, translations.gameModes().findExact("Выживание").single().value)
    }

    @Test
    @Suppress("DEPRECATION")
    fun `translate resolves against a pre-1_13 table via legacy keys`() {
        assertEquals("Острота", legacyTranslations.translate(Enchantment.DAMAGE_ALL).text)
        assertEquals("Спешка", legacyTranslations.translate(PotionEffectType.FAST_DIGGING).text)
        assertEquals("Зомби", legacyTranslations.translate(EntityType.ZOMBIE).text)
        assertTrue(legacyTranslations.translate(EntityType.ZOMBIE).localized)
    }
}
