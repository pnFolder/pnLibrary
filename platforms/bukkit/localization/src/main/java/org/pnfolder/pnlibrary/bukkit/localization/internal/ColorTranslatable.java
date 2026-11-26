package org.pnfolder.pnlibrary.bukkit.localization.internal;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.bukkit.DyeColor;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.localization.TranslatableType;
import org.pnfolder.pnlibrary.localization.TranslationCategory;

/** Translation key of a {@link DyeColor}. */
public final class ColorTranslatable implements TranslatableType<DyeColor> {
    public static final ColorTranslatable INSTANCE = new ColorTranslatable();

    private ColorTranslatable() {
    }

    @NotNull
    @Override
    public List<String> keysOf(DyeColor value) {
        return Collections.singletonList(TranslationCategory.COLOR.key(value.name().toLowerCase(Locale.ROOT)));
    }

    @NotNull
    @Override
    public String fallbackName(DyeColor value) {
        return TranslationNames.readable(value.name());
    }
}
