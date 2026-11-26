package org.pnfolder.pnlibrary.bukkit.localization.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.enchantments.Enchantment;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.localization.TranslatableType;
import org.pnfolder.pnlibrary.localization.TranslationCategory;

/** Translation keys of an {@link Enchantment}: the modern key, then the pre-1.13 one. */
public final class EnchantmentTranslatable implements TranslatableType<Enchantment> {
    public static final EnchantmentTranslatable INSTANCE = new EnchantmentTranslatable();

    private EnchantmentTranslatable() {
    }

    @NotNull
    @Override
    public List<String> keysOf(Enchantment value) {
        String modern = modernPath(value);
        List<String> keys = new ArrayList<>();
        keys.add(TranslationCategory.ENCHANTMENT.key(modern));
        String legacy = TranslationLegacyNames.ENCHANTMENT_LEGACY.get(modern);
        if (legacy != null) {
            keys.add("enchantment." + legacy);
        }
        return TranslationNames.distinct(keys);
    }

    @NotNull
    @Override
    public String fallbackName(Enchantment value) {
        return TranslationNames.readable(TranslationNames.legacyName(value));
    }

    private static String modernPath(Enchantment value) {
        String key = TranslationNames.namespacedKey(value);
        if (key != null) {
            return TranslationNames.afterColon(key);
        }
        String upper = TranslationNames.legacyName(value).toUpperCase(Locale.ROOT);
        String modern = TranslationLegacyNames.ENCHANTMENT_LEGACY_TO_MODERN.get(upper);
        return (modern != null ? modern : upper).toLowerCase(Locale.ROOT);
    }
}
