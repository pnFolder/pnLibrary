package org.pnfolder.pnlibrary.bukkit.localization;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.bukkit.localization.internal.BukkitTranslationIndexes;
import org.pnfolder.pnlibrary.localization.LocaleTranslations;
import org.pnfolder.pnlibrary.localization.Translation;
import org.pnfolder.pnlibrary.localization.TranslatableType;
import org.pnfolder.pnlibrary.localization.TranslationIndex;
import org.pnfolder.pnlibrary.localization.TranslationMetadata;
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayouts;

/** Delegates the neutral contract to the wrapped table and builds each reverse index on first use, since most callers need one. */
final class BukkitLocaleTranslationsAdapter implements BukkitLocaleTranslations {
    private final LocaleTranslations delegate;
    private final Lazy<TranslationIndex<Material>> materials;
    private final Lazy<TranslationIndex<Enchantment>> enchantments;
    private final Lazy<TranslationIndex<EntityType>> entities;
    private final Lazy<TranslationIndex<PotionEffectType>> effects;
    private final Lazy<TranslationIndex<Biome>> biomes;
    private final Lazy<TranslationIndex<DyeColor>> colors;
    private final Lazy<TranslationIndex<GameMode>> gameModes;

    BukkitLocaleTranslationsAdapter(LocaleTranslations delegate) {
        this.delegate = delegate;
        this.materials = new Lazy<>(() -> BukkitTranslationIndexes.materials(delegate.translations(), delegate.getLayouts()));
        this.enchantments = new Lazy<>(() -> BukkitTranslationIndexes.enchantments(delegate.translations(), delegate.getLayouts()));
        this.entities = new Lazy<>(() -> BukkitTranslationIndexes.entities(delegate.translations(), delegate.getLayouts()));
        this.effects = new Lazy<>(() -> BukkitTranslationIndexes.effects(delegate.translations(), delegate.getLayouts()));
        this.biomes = new Lazy<>(() -> BukkitTranslationIndexes.biomes(delegate.translations(), delegate.getLayouts()));
        this.colors = new Lazy<>(() -> BukkitTranslationIndexes.colors(delegate.translations(), delegate.getLayouts()));
        this.gameModes = new Lazy<>(() -> BukkitTranslationIndexes.gameModes(delegate.translations(), delegate.getLayouts()));
    }

    @NotNull
    @Override
    public String getLocale() {
        return delegate.getLocale();
    }

    @NotNull
    @Override
    public TranslationMetadata getMetadata() {
        return delegate.getMetadata();
    }

    @NotNull
    @Override
    public KeyboardLayouts getLayouts() {
        return delegate.getLayouts();
    }

    @NotNull
    @Override
    public Optional<String> translate(@NotNull String key) {
        return delegate.translate(key);
    }

    @NotNull
    @Override
    public String translateOrKey(@NotNull String key) {
        return delegate.translateOrKey(key);
    }

    @NotNull
    @Override
    public <T> Translation translate(@NotNull TranslatableType<T> type, T value) {
        return delegate.translate(type, value);
    }

    @NotNull
    @Override
    public Map<String, String> translations() {
        return delegate.translations();
    }

    @NotNull
    @Override
    public TranslationIndex<String> keys() {
        return delegate.keys();
    }

    @NotNull
    @Override
    public TranslationIndex<Material> materials() {
        return materials.get();
    }

    @NotNull
    @Override
    public TranslationIndex<Enchantment> enchantments() {
        return enchantments.get();
    }

    @NotNull
    @Override
    public TranslationIndex<EntityType> entities() {
        return entities.get();
    }

    @NotNull
    @Override
    public TranslationIndex<PotionEffectType> effects() {
        return effects.get();
    }

    @NotNull
    @Override
    public TranslationIndex<Biome> biomes() {
        return biomes.get();
    }

    @NotNull
    @Override
    public TranslationIndex<DyeColor> colors() {
        return colors.get();
    }

    @NotNull
    @Override
    public TranslationIndex<GameMode> gameModes() {
        return gameModes.get();
    }

    /** Computes the value once, on first use; safe under concurrent callers. */
    private static final class Lazy<T> {
        private final Supplier<T> source;
        private volatile T value;

        Lazy(Supplier<T> source) {
            this.source = source;
        }

        T get() {
            T current = value;
            if (current != null) {
                return current;
            }
            synchronized (this) {
                if (value == null) {
                    value = source.get();
                }
                return value;
            }
        }
    }
}
