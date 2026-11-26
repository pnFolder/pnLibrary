package org.pnfolder.pnlibrary.bukkit.localization;

import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionEffectType;
import org.pnfolder.pnlibrary.bukkit.localization.internal.BiomeTranslatable;
import org.pnfolder.pnlibrary.bukkit.localization.internal.ColorTranslatable;
import org.pnfolder.pnlibrary.bukkit.localization.internal.EffectTranslatable;
import org.pnfolder.pnlibrary.bukkit.localization.internal.EnchantmentTranslatable;
import org.pnfolder.pnlibrary.bukkit.localization.internal.EntityTranslatable;
import org.pnfolder.pnlibrary.bukkit.localization.internal.GameModeTranslatable;
import org.pnfolder.pnlibrary.bukkit.localization.internal.MaterialTranslatable;
import org.pnfolder.pnlibrary.localization.TranslatableType;

/** {@link TranslatableType} categories for Bukkit value types, passed to {@code LocaleTranslations.translate}. */
public final class BukkitTranslatables {
    /** Resolves Bukkit {@link Material} values to their Minecraft translation keys. */
    public static final TranslatableType<Material> MATERIAL = MaterialTranslatable.INSTANCE;

    /** Resolves Bukkit {@link Enchantment} values to their Minecraft translation keys. */
    public static final TranslatableType<Enchantment> ENCHANTMENT = EnchantmentTranslatable.INSTANCE;

    /** Resolves Bukkit {@link EntityType} values to their Minecraft translation keys. */
    public static final TranslatableType<EntityType> ENTITY = EntityTranslatable.INSTANCE;

    /** Resolves Bukkit {@link PotionEffectType} values to their Minecraft translation keys. */
    public static final TranslatableType<PotionEffectType> EFFECT = EffectTranslatable.INSTANCE;

    /** Resolves Bukkit {@link Biome} values to their Minecraft translation keys. */
    public static final TranslatableType<Biome> BIOME = BiomeTranslatable.INSTANCE;

    /** Resolves Bukkit {@link DyeColor} values to their Minecraft translation keys. */
    public static final TranslatableType<DyeColor> COLOR = ColorTranslatable.INSTANCE;

    /** Resolves Bukkit {@link GameMode} values to their Minecraft translation keys. */
    public static final TranslatableType<GameMode> GAME_MODE = GameModeTranslatable.INSTANCE;

    private BukkitTranslatables() {
    }
}
