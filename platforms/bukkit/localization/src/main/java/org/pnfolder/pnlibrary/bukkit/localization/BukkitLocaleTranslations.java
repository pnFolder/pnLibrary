package org.pnfolder.pnlibrary.bukkit.localization;

import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.localization.LocaleTranslations;
import org.pnfolder.pnlibrary.localization.Translation;
import org.pnfolder.pnlibrary.localization.TranslationIndex;

/** {@link LocaleTranslations} enriched with Bukkit-specific lookups by material, enchantment, entity, effect, biome, color and game mode. */
public interface BukkitLocaleTranslations extends LocaleTranslations {

    /** Wraps a neutral {@code translations} table, resolving its entries to Bukkit value types. */
    @NotNull
    static BukkitLocaleTranslations wrap(@NotNull LocaleTranslations translations) {
        return new BukkitLocaleTranslationsAdapter(translations);
    }

    /** Translates {@code material}; a convenience for {@code translate(BukkitTranslatables.MATERIAL, material)}. */
    @NotNull
    default Translation translate(@NotNull Material material) {
        return translate(BukkitTranslatables.MATERIAL, material);
    }

    /** Translates {@code enchantment}; a convenience for {@code translate(BukkitTranslatables.ENCHANTMENT, enchantment)}. */
    @NotNull
    default Translation translate(@NotNull Enchantment enchantment) {
        return translate(BukkitTranslatables.ENCHANTMENT, enchantment);
    }

    /** Translates {@code entityType}; a convenience for {@code translate(BukkitTranslatables.ENTITY, entityType)}. */
    @NotNull
    default Translation translate(@NotNull EntityType entityType) {
        return translate(BukkitTranslatables.ENTITY, entityType);
    }

    /** Translates {@code effect}; a convenience for {@code translate(BukkitTranslatables.EFFECT, effect)}. */
    @NotNull
    default Translation translate(@NotNull PotionEffectType effect) {
        return translate(BukkitTranslatables.EFFECT, effect);
    }

    /** Translates {@code biome}; a convenience for {@code translate(BukkitTranslatables.BIOME, biome)}. */
    @NotNull
    default Translation translate(@NotNull Biome biome) {
        return translate(BukkitTranslatables.BIOME, biome);
    }

    /** Translates {@code color}; a convenience for {@code translate(BukkitTranslatables.COLOR, color)}. */
    @NotNull
    default Translation translate(@NotNull DyeColor color) {
        return translate(BukkitTranslatables.COLOR, color);
    }

    /** Translates {@code gameMode}; a convenience for {@code translate(BukkitTranslatables.GAME_MODE, gameMode)}. */
    @NotNull
    default Translation translate(@NotNull GameMode gameMode) {
        return translate(BukkitTranslatables.GAME_MODE, gameMode);
    }

    /** Reverse-search index from localized item and block names to {@link Material}. */
    @NotNull TranslationIndex<Material> materials();

    /** Reverse-search index from localized enchantment names to {@link Enchantment}. */
    @NotNull TranslationIndex<Enchantment> enchantments();

    /** Reverse-search index from localized entity names to {@link EntityType}. */
    @NotNull TranslationIndex<EntityType> entities();

    /** Reverse-search index from localized potion effect names to {@link PotionEffectType}. */
    @NotNull TranslationIndex<PotionEffectType> effects();

    /** Reverse-search index from localized biome names to {@link Biome}. */
    @NotNull TranslationIndex<Biome> biomes();

    /** Reverse-search index from localized dye color names to {@link DyeColor}. */
    @NotNull TranslationIndex<DyeColor> colors();

    /** Reverse-search index from localized game mode names to {@link GameMode}. */
    @NotNull TranslationIndex<GameMode> gameModes();
}
