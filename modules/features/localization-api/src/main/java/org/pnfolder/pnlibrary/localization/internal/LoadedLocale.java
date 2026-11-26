package org.pnfolder.pnlibrary.localization.internal;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.localization.LocaleTranslations;
import org.pnfolder.pnlibrary.localization.TranslationIndex;
import org.pnfolder.pnlibrary.localization.TranslationMetadata;
import org.pnfolder.pnlibrary.localization.TranslationSource;
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayouts;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Immutable translations of one locale together with their metadata and fuzzy-search indexes. */
final class LoadedLocale implements LocaleTranslations {
    private final String locale;
    private final LocaleData data;
    private final TranslationMetadata metadata;
    private final KeyboardLayouts layouts;

    private LoadedLocale(String locale, LocaleData data, TranslationMetadata metadata, KeyboardLayouts layouts) {
        this.locale = locale;
        this.data = data;
        this.metadata = metadata;
        this.layouts = layouts;
    }

    LoadedLocale(String locale, Map<String, String> values, TranslationMetadata metadata, KeyboardLayouts layouts) {
        this(locale, new LocaleData(values, layouts), metadata, layouts);
    }

    @NotNull
    @Override
    public String getLocale() {
        return locale;
    }

    @NotNull
    @Override
    public TranslationMetadata getMetadata() {
        return metadata;
    }

    @NotNull
    @Override
    public KeyboardLayouts getLayouts() {
        return layouts;
    }

    @NotNull
    @Override
    public Optional<String> translate(@NotNull String key) {
        return Optional.ofNullable(data.values.get(key));
    }

    @NotNull
    @Override
    public Map<String, String> translations() {
        return data.values;
    }

    @NotNull
    @Override
    public TranslationIndex<String> keys() {
        return data.keys();
    }

    /** Copy that reports a different origin, for example MEMORY when served from the cache. */
    LoadedLocale withSource(TranslationSource source) {
        return new LoadedLocale(locale, data,
                new TranslationMetadata(source, metadata.getSha1(), metadata.isStale()), layouts);
    }

    /** Shared immutable values with a lazily built key index, so copies by {@link #withSource} reuse it. */
    private static final class LocaleData {
        final Map<String, String> values;
        private final KeyboardLayouts layouts;
        private TranslationIndex<String> keys;

        LocaleData(Map<String, String> values, KeyboardLayouts layouts) {
            this.values = Collections.unmodifiableMap(new LinkedHashMap<String, String>(values));
            this.layouts = layouts;
        }

        synchronized TranslationIndex<String> keys() {
            if (keys == null) {
                keys = TranslationSearchIndex.keys(values, layouts);
            }
            return keys;
        }
    }
}
