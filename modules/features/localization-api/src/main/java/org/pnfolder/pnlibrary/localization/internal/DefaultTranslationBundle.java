package org.pnfolder.pnlibrary.localization.internal;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion;
import org.pnfolder.pnlibrary.localization.LocaleTranslations;
import org.pnfolder.pnlibrary.localization.TranslationBundle;
import org.pnfolder.pnlibrary.localization.TranslationRequest;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Bundle of the locales loaded for one request; the optional fallback fills keys missing from a locale. */
final class DefaultTranslationBundle implements TranslationBundle {
    private final MinecraftVersion version;
    private final Set<String> locales;
    private final String fallback;
    private final Map<String, LoadedLocale> loaded;

    DefaultTranslationBundle(MinecraftVersion version, Set<String> requested, String fallback,
                             Map<String, LoadedLocale> loaded) {
        this.version = version;
        this.locales = Collections.unmodifiableSet(new LinkedHashSet<>(requested));
        this.fallback = fallback;
        this.loaded = loaded;
    }

    @NotNull
    @Override
    public MinecraftVersion getVersion() {
        return version;
    }

    @NotNull
    @Override
    public Set<String> getLocales() {
        return locales;
    }

    @NotNull
    @Override
    public LocaleTranslations locale(@NotNull String id) {
        String normalized = TranslationRequest.normalizeLocale(id);
        LoadedLocale primary = loaded.get(normalized);
        if (primary == null) {
            throw new IllegalArgumentException("Locale " + normalized + " was not requested");
        }
        LoadedLocale fallbackLocale = fallback == null ? null : loaded.get(fallback);
        return fallbackLocale == null || fallbackLocale == primary ? primary : new FallbackLocale(primary, fallbackLocale);
    }
}
