package org.pnfolder.pnlibrary.bukkit.localization;

import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.localization.LocaleTranslations;
import org.pnfolder.pnlibrary.localization.TranslationIndex;
import org.pnfolder.pnlibrary.localization.TranslationMetadata;
import org.pnfolder.pnlibrary.localization.TranslationSource;
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayouts;

final class FakeLocaleTranslations implements LocaleTranslations {
    private final Map<String, String> values;
    private final KeyboardLayouts layouts;

    FakeLocaleTranslations(Map<String, String> values) {
        this(values, KeyboardLayouts.builtIn);
    }

    FakeLocaleTranslations(Map<String, String> values, KeyboardLayouts layouts) {
        this.values = values;
        this.layouts = layouts;
    }

    @NotNull
    @Override
    public KeyboardLayouts getLayouts() {
        return layouts;
    }

    @NotNull
    @Override
    public String getLocale() {
        return "ru_ru";
    }

    @NotNull
    @Override
    public TranslationMetadata getMetadata() {
        return new TranslationMetadata(TranslationSource.MEMORY, null, false);
    }

    @NotNull
    @Override
    public Optional<String> translate(@NotNull String key) {
        return Optional.ofNullable(values.get(key));
    }

    @NotNull
    @Override
    public Map<String, String> translations() {
        return values;
    }

    @NotNull
    @Override
    public TranslationIndex<String> keys() {
        throw new IllegalStateException("not needed for this test");
    }
}
