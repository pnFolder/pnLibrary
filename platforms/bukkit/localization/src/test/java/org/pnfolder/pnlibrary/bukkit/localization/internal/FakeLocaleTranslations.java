package org.pnfolder.pnlibrary.bukkit.localization.internal;

import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.localization.LocaleTranslations;
import org.pnfolder.pnlibrary.localization.TranslationIndex;
import org.pnfolder.pnlibrary.localization.TranslationMetadata;
import org.pnfolder.pnlibrary.localization.TranslationSource;

final class FakeLocaleTranslations implements LocaleTranslations {
    private final Map<String, String> values;

    FakeLocaleTranslations(Map<String, String> values) {
        this.values = values;
    }

    @NotNull
    @Override
    public String getLocale() {
        return "ru";
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
