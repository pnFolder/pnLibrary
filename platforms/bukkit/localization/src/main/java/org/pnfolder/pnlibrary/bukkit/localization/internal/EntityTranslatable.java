package org.pnfolder.pnlibrary.bukkit.localization.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.localization.TranslatableType;
import org.pnfolder.pnlibrary.localization.TranslationCategory;

/** Translation keys of an {@link EntityType}: the modern key, then the pre-1.13 one. */
public final class EntityTranslatable implements TranslatableType<EntityType> {
    public static final EntityTranslatable INSTANCE = new EntityTranslatable();

    private EntityTranslatable() {
    }

    @NotNull
    @Override
    public List<String> keysOf(EntityType value) {
        String key = TranslationNames.namespacedKey(value);
        String path = key != null ? TranslationNames.afterColon(key) : value.name().toLowerCase(Locale.ROOT);
        // Pre-1.13 entity keys are best-effort: capitalized names, a few historical spellings mapped below.
        String pascal = TranslationLegacyNames.ENTITY_LEGACY.get(path);
        if (pascal == null) {
            pascal = TranslationNames.pascalCase(path);
        }
        List<String> keys = new ArrayList<>();
        keys.add(TranslationCategory.ENTITY.key(path));
        keys.add("entity." + pascal + ".name");
        return TranslationNames.distinct(keys);
    }

    @NotNull
    @Override
    public String fallbackName(EntityType value) {
        return TranslationNames.readable(value.name());
    }
}
