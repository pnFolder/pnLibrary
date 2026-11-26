package org.pnfolder.pnlibrary.bukkit.localization.internal;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.bukkit.block.Biome;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.localization.TranslatableType;
import org.pnfolder.pnlibrary.localization.TranslationCategory;

/** Translation key of a {@link Biome}. */
public final class BiomeTranslatable implements TranslatableType<Biome> {
    public static final BiomeTranslatable INSTANCE = new BiomeTranslatable();

    private BiomeTranslatable() {
    }

    @NotNull
    @Override
    public List<String> keysOf(Biome value) {
        String key = TranslationNames.namespacedKey(value);
        String path = key != null ? TranslationNames.afterColon(key) : value.name().toLowerCase(Locale.ROOT);
        return Collections.singletonList(TranslationCategory.BIOME.key(path));
    }

    @NotNull
    @Override
    public String fallbackName(Biome value) {
        return TranslationNames.readable(value.name());
    }
}
