package org.pnfolder.pnlibrary.localization.internal;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.localization.LocaleTranslations;
import org.pnfolder.pnlibrary.localization.Translation;
import org.pnfolder.pnlibrary.localization.TranslatableType;
import org.pnfolder.pnlibrary.localization.TranslationIndex;
import org.pnfolder.pnlibrary.localization.TranslationMetadata;
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayouts;

import java.util.Map;
import java.util.Optional;

/**
 * Locale view that asks the fallback for keys the primary locale lacks. Every other member is forwarded to the
 * primary locale, exactly as the Kotlin delegation it replaces did.
 */
final class FallbackLocale implements LocaleTranslations {
    private final LoadedLocale primary;
    private final LoadedLocale fallback;

    FallbackLocale(LoadedLocale primary, LoadedLocale fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @NotNull
    @Override
    public String getLocale() {
        return primary.getLocale();
    }

    @NotNull
    @Override
    public TranslationMetadata getMetadata() {
        return primary.getMetadata();
    }

    @NotNull
    @Override
    public KeyboardLayouts getLayouts() {
        return primary.getLayouts();
    }

    @NotNull
    @Override
    public Optional<String> translate(@NotNull String key) {
        Optional<String> value = primary.translate(key);
        return value.isPresent() ? value : fallback.translate(key);
    }

    @NotNull
    @Override
    public String translateOrKey(@NotNull String key) {
        return primary.translateOrKey(key);
    }

    @NotNull
    @Override
    public <T> Translation translate(@NotNull TranslatableType<T> type, T value) {
        return primary.translate(type, value);
    }

    @NotNull
    @Override
    public Map<String, String> translations() {
        return primary.translations();
    }

    @NotNull
    @Override
    public TranslationIndex<String> keys() {
        return primary.keys();
    }
}
