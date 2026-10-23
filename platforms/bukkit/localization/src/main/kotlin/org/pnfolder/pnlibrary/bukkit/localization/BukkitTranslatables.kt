package org.pnfolder.pnlibrary.bukkit.localization

import org.bukkit.DyeColor
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.block.Biome
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.EntityType
import org.bukkit.potion.PotionEffectType
import org.pnfolder.pnlibrary.bukkit.localization.internal.BiomeTranslatable
import org.pnfolder.pnlibrary.bukkit.localization.internal.ColorTranslatable
import org.pnfolder.pnlibrary.bukkit.localization.internal.EffectTranslatable
import org.pnfolder.pnlibrary.bukkit.localization.internal.EnchantmentTranslatable
import org.pnfolder.pnlibrary.bukkit.localization.internal.EntityTranslatable
import org.pnfolder.pnlibrary.bukkit.localization.internal.GameModeTranslatable
import org.pnfolder.pnlibrary.bukkit.localization.internal.MaterialTranslatable
import org.pnfolder.pnlibrary.localization.TranslatableType

/** [TranslatableType] categories for Bukkit value types, passed to [org.pnfolder.pnlibrary.localization.LocaleTranslations.translate]. */
object BukkitTranslatables {
    /** Resolves Bukkit [Material] values to their Minecraft translation keys. */
    @JvmField
    val MATERIAL: TranslatableType<Material> = MaterialTranslatable

    /** Resolves Bukkit [Enchantment] values to their Minecraft translation keys. */
    @JvmField
    val ENCHANTMENT: TranslatableType<Enchantment> = EnchantmentTranslatable

    /** Resolves Bukkit [EntityType] values to their Minecraft translation keys. */
    @JvmField
    val ENTITY: TranslatableType<EntityType> = EntityTranslatable

    /** Resolves Bukkit [PotionEffectType] values to their Minecraft translation keys. */
    @JvmField
    val EFFECT: TranslatableType<PotionEffectType> = EffectTranslatable

    /** Resolves Bukkit [Biome] values to their Minecraft translation keys. */
    @JvmField
    val BIOME: TranslatableType<Biome> = BiomeTranslatable

    /** Resolves Bukkit [DyeColor] values to their Minecraft translation keys. */
    @JvmField
    val COLOR: TranslatableType<DyeColor> = ColorTranslatable

    /** Resolves Bukkit [GameMode] values to their Minecraft translation keys. */
    @JvmField
    val GAME_MODE: TranslatableType<GameMode> = GameModeTranslatable
}
