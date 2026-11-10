package org.pnfolder.pnlibrary.bukkit.localization

import org.bukkit.DyeColor
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.block.Biome
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.EntityType
import org.bukkit.potion.PotionEffectType
import org.pnfolder.pnlibrary.bukkit.localization.internal.bukkitBiomes
import org.pnfolder.pnlibrary.bukkit.localization.internal.bukkitColors
import org.pnfolder.pnlibrary.bukkit.localization.internal.bukkitEffects
import org.pnfolder.pnlibrary.bukkit.localization.internal.bukkitEnchantments
import org.pnfolder.pnlibrary.bukkit.localization.internal.bukkitEntities
import org.pnfolder.pnlibrary.bukkit.localization.internal.bukkitGameModes
import org.pnfolder.pnlibrary.bukkit.localization.internal.bukkitMaterials
import org.pnfolder.pnlibrary.localization.LocaleTranslations
import org.pnfolder.pnlibrary.localization.Translation
import org.pnfolder.pnlibrary.localization.TranslationIndex

/** [LocaleTranslations] enriched with Bukkit-specific lookups by material, enchantment, entity, effect, biome, color and game mode. */
interface BukkitLocaleTranslations : LocaleTranslations {
    /** Translates [material]; a convenience for `translate(BukkitTranslatables.MATERIAL, material)`. */
    fun translate(material: Material): Translation = translate(BukkitTranslatables.MATERIAL, material)

    /** Translates [enchantment]; a convenience for `translate(BukkitTranslatables.ENCHANTMENT, enchantment)`. */
    fun translate(enchantment: Enchantment): Translation = translate(BukkitTranslatables.ENCHANTMENT, enchantment)

    /** Translates [entityType]; a convenience for `translate(BukkitTranslatables.ENTITY, entityType)`. */
    fun translate(entityType: EntityType): Translation = translate(BukkitTranslatables.ENTITY, entityType)

    /** Translates [effect]; a convenience for `translate(BukkitTranslatables.EFFECT, effect)`. */
    fun translate(effect: PotionEffectType): Translation = translate(BukkitTranslatables.EFFECT, effect)

    /** Translates [biome]; a convenience for `translate(BukkitTranslatables.BIOME, biome)`. */
    fun translate(biome: Biome): Translation = translate(BukkitTranslatables.BIOME, biome)

    /** Translates [color]; a convenience for `translate(BukkitTranslatables.COLOR, color)`. */
    fun translate(color: DyeColor): Translation = translate(BukkitTranslatables.COLOR, color)

    /** Translates [gameMode]; a convenience for `translate(BukkitTranslatables.GAME_MODE, gameMode)`. */
    fun translate(gameMode: GameMode): Translation = translate(BukkitTranslatables.GAME_MODE, gameMode)

    /** Reverse-search index from localized item and block names to [Material]. */
    fun materials(): TranslationIndex<Material>

    /** Reverse-search index from localized enchantment names to [Enchantment]. */
    fun enchantments(): TranslationIndex<Enchantment>

    /** Reverse-search index from localized entity names to [EntityType]. */
    fun entities(): TranslationIndex<EntityType>

    /** Reverse-search index from localized potion effect names to [PotionEffectType]. */
    fun effects(): TranslationIndex<PotionEffectType>

    /** Reverse-search index from localized biome names to [Biome]. */
    fun biomes(): TranslationIndex<Biome>

    /** Reverse-search index from localized dye color names to [DyeColor]. */
    fun colors(): TranslationIndex<DyeColor>

    /** Reverse-search index from localized game mode names to [GameMode]. */
    fun gameModes(): TranslationIndex<GameMode>

    /** Factory for wrapping neutral translation tables. */
    companion object {
        /** Wraps a neutral [translations] table, resolving its entries to Bukkit value types. */
        @JvmStatic
        fun wrap(translations: LocaleTranslations): BukkitLocaleTranslations = BukkitLocaleTranslationsAdapter(translations)
    }
}

/** Delegates the neutral contract to [delegate] and builds each reverse index on first use, since most callers need one. */
private class BukkitLocaleTranslationsAdapter(
    private val delegate: LocaleTranslations,
) : BukkitLocaleTranslations, LocaleTranslations by delegate {
    private val materialsIndex by lazy { bukkitMaterials(delegate.translations(), delegate.layouts) }
    private val enchantmentsIndex by lazy { bukkitEnchantments(delegate.translations(), delegate.layouts) }
    private val entitiesIndex by lazy { bukkitEntities(delegate.translations(), delegate.layouts) }
    private val effectsIndex by lazy { bukkitEffects(delegate.translations(), delegate.layouts) }
    private val biomesIndex by lazy { bukkitBiomes(delegate.translations(), delegate.layouts) }
    private val colorsIndex by lazy { bukkitColors(delegate.translations(), delegate.layouts) }
    private val gameModesIndex by lazy { bukkitGameModes(delegate.translations(), delegate.layouts) }

    override fun materials(): TranslationIndex<Material> = materialsIndex
    override fun enchantments(): TranslationIndex<Enchantment> = enchantmentsIndex
    override fun entities(): TranslationIndex<EntityType> = entitiesIndex
    override fun effects(): TranslationIndex<PotionEffectType> = effectsIndex
    override fun biomes(): TranslationIndex<Biome> = biomesIndex
    override fun colors(): TranslationIndex<DyeColor> = colorsIndex
    override fun gameModes(): TranslationIndex<GameMode> = gameModesIndex
}
