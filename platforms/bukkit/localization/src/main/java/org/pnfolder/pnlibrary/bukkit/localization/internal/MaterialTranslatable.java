package org.pnfolder.pnlibrary.bukkit.localization.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.localization.TranslatableType;
import org.pnfolder.pnlibrary.localization.TranslationCategory;

/**
 * Translation keys of a {@link Material}, block and item variants. Keys come modern (1.13+) first, then pre-1.13
 * legacy ones; {@code translate} picks the first the loaded table contains, so the version-correct key wins.
 */
public final class MaterialTranslatable implements TranslatableType<Material> {
    public static final MaterialTranslatable INSTANCE = new MaterialTranslatable();

    private MaterialTranslatable() {
    }

    @NotNull
    @Override
    public List<String> keysOf(Material value) {
        String path = value.name().toLowerCase(Locale.ROOT);
        List<TranslationCategory> modern = value.isBlock()
            ? Arrays.asList(TranslationCategory.BLOCK, TranslationCategory.ITEM)
            : Arrays.asList(TranslationCategory.ITEM, TranslationCategory.BLOCK);
        // Pre-1.13 blocks/items are best-effort: many used camelCase names or data-value variants and fall back.
        String camel = TranslationNames.camelCase(value.name());
        List<String> keys = new ArrayList<>();
        String exact = TranslationNames.translationKey(value);
        if (exact != null) {
            keys.add(exact);
        }
        for (TranslationCategory category : modern) {
            keys.add(category.key(path));
        }
        keys.add("tile." + camel + ".name");
        keys.add("item." + camel + ".name");
        return TranslationNames.distinct(keys);
    }

    @NotNull
    @Override
    public String fallbackName(Material value) {
        return TranslationNames.readable(value.name());
    }
}
