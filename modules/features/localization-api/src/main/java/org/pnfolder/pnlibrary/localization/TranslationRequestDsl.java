package org.pnfolder.pnlibrary.localization;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion;

import java.util.LinkedHashSet;
import java.util.Set;

/** Collects the settings of a {@link TranslationRequest} for {@link MinecraftLocalizationDsl#load}. */
public final class TranslationRequestDsl {
    private final Set<String> locales = new LinkedHashSet<>();
    private MinecraftVersion version;
    private String fallback;

    TranslationRequestDsl() {
    }

    /** Minecraft version whose translation data should be loaded. */
    @Nullable
    public MinecraftVersion getVersion() {
        return version;
    }

    /** Sets the Minecraft version whose translation data should be loaded. */
    public void setVersion(@NotNull MinecraftVersion version) {
        this.version = version;
    }

    /** Adds preferred locale identifiers in lookup order. */
    public void locales(@NotNull String... values) {
        for (String value : values) {
            locales.add(value);
        }
    }

    /** Adds preferred typed locales in lookup order. */
    public void locales(@NotNull MinecraftLocale... values) {
        for (MinecraftLocale value : values) {
            locales.add(value.getCode());
        }
    }

    /** Sets the optional fallback locale. */
    public void fallback(@NotNull String locale) {
        fallback = locale;
    }

    /** Sets the optional fallback to a typed locale. */
    public void fallback(@NotNull MinecraftLocale locale) {
        fallback = locale.getCode();
    }

    /** Validates the collected settings and creates the request; fails when the version was not set. */
    TranslationRequest build() {
        if (version == null) {
            throw new IllegalArgumentException("Minecraft version must be set");
        }
        return TranslationRequest.builder().version(version).locales(locales).fallback(fallback).build();
    }
}
