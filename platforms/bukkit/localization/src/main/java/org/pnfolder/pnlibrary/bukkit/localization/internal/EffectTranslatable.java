package org.pnfolder.pnlibrary.bukkit.localization.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.localization.TranslatableType;
import org.pnfolder.pnlibrary.localization.TranslationCategory;

/** Translation keys of a {@link PotionEffectType}: the modern key, then the pre-1.13 one. */
public final class EffectTranslatable implements TranslatableType<PotionEffectType> {
    public static final EffectTranslatable INSTANCE = new EffectTranslatable();

    private EffectTranslatable() {
    }

    @NotNull
    @Override
    public List<String> keysOf(PotionEffectType value) {
        String modern = modernPath(value);
        List<String> keys = new ArrayList<>();
        keys.add(TranslationCategory.EFFECT.key(modern));
        String legacy = TranslationLegacyNames.EFFECT_LEGACY.get(modern);
        if (legacy != null) {
            keys.add("effect." + legacy);
        }
        return TranslationNames.distinct(keys);
    }

    @NotNull
    @Override
    public String fallbackName(PotionEffectType value) {
        return TranslationNames.readable(TranslationNames.effectName(value));
    }

    private static String modernPath(PotionEffectType value) {
        String key = TranslationNames.namespacedKey(value);
        if (key != null) {
            return TranslationNames.afterColon(key);
        }
        String upper = TranslationNames.effectName(value).toUpperCase(Locale.ROOT);
        String modern = TranslationLegacyNames.EFFECT_LEGACY_TO_MODERN.get(upper);
        return (modern != null ? modern : upper).toLowerCase(Locale.ROOT);
    }
}
