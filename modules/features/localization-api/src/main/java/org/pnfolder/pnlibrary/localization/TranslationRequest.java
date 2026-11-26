package org.pnfolder.pnlibrary.localization;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Immutable request for exact Minecraft version/locale translation tables. */
public final class TranslationRequest {
    /** Shape of a normalized locale id, such as {@code en_us}. */
    private static final Pattern LOCALE = Pattern.compile("[a-z0-9]+(?:_[a-z0-9]+)*");

    private final MinecraftVersion version;
    private final Set<String> locales;
    private final String fallbackLocale;

    private TranslationRequest(MinecraftVersion version, Set<String> locales, String fallbackLocale) {
        this.version = version;
        this.locales = locales;
        this.fallbackLocale = fallbackLocale;
    }

    /** Known Minecraft version to load. */
    @NotNull
    public MinecraftVersion getVersion() {
        return version;
    }

    /** Normalized locale identifiers to load. */
    @NotNull
    public Set<String> getLocales() {
        return locales;
    }

    /** Optional normalized fallback locale. */
    @Nullable
    public String getFallbackLocale() {
        return fallbackLocale;
    }

    /** Returns an empty translation-request builder. */
    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Lowercases {@code value}, turns {@code -} into {@code _} and rejects anything that is not a Minecraft locale id.
     * Used by the localization service; plugin code does not need it.
     */
    @ApiStatus.Internal
    @NotNull
    public static String normalizeLocale(@NotNull String value) {
        String normalized = MinecraftLocale.strip(value).toLowerCase(Locale.ROOT).replace('-', '_');
        if (!LOCALE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid Minecraft locale: " + value);
        }
        return normalized;
    }

    /** Fluent builder for {@link TranslationRequest}. */
    public static final class Builder {
        private MinecraftVersion version;
        private final Set<String> locales = new LinkedHashSet<>();
        private String fallbackLocale;

        Builder() {
        }

        /** Selects the known Minecraft version. */
        @NotNull
        public Builder version(@NotNull MinecraftVersion value) {
            version = value;
            return this;
        }

        /** Adds one locale after normalization. */
        @NotNull
        public Builder locale(@NotNull String value) {
            locales.add(normalizeLocale(value));
            return this;
        }

        /** Adds one typed locale. */
        @NotNull
        public Builder locale(@NotNull MinecraftLocale locale) {
            return locale(locale.getCode());
        }

        /** Adds all supplied locale identifiers. */
        @NotNull
        public Builder locales(@NotNull String... values) {
            for (String value : values) {
                locale(value);
            }
            return this;
        }

        /** Adds all supplied typed locales in lookup order. */
        @NotNull
        public Builder locales(@NotNull MinecraftLocale... values) {
            for (MinecraftLocale value : values) {
                locale(value.getCode());
            }
            return this;
        }

        /** Adds all locale identifiers in {@code values}. */
        @NotNull
        public Builder locales(@NotNull Collection<String> values) {
            for (String value : values) {
                locale(value);
            }
            return this;
        }

        /** Sets the optional fallback locale after normalization. */
        @NotNull
        public Builder fallback(@Nullable String value) {
            fallbackLocale = value == null ? null : normalizeLocale(value);
            return this;
        }

        /** Sets the optional fallback to a typed locale. */
        @NotNull
        public Builder fallback(@NotNull MinecraftLocale locale) {
            return fallback(locale.getCode());
        }

        /** Validates and creates the immutable request. */
        @NotNull
        public TranslationRequest build() {
            if (version == null) {
                throw new IllegalArgumentException("Minecraft version is required");
            }
            if (!version.isKnown()) {
                throw new IllegalArgumentException("UNKNOWN Minecraft version is not supported");
            }
            if (locales.isEmpty()) {
                throw new IllegalArgumentException("At least one locale is required");
            }
            return new TranslationRequest(
                    version, Collections.unmodifiableSet(new LinkedHashSet<>(locales)), fallbackLocale);
        }
    }
}
