package org.pnfolder.pnlibrary.localization;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion;
import org.pnfolder.pnlibrary.localization.internal.DefaultMinecraftLocalization;
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayouts;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;

/**
 * Loads and caches Mojang translation data for explicit Minecraft versions.
 *
 * <p>Network and disk operations complete asynchronously. Closing the service releases owned executors and rejects
 * new requests.
 */
public interface MinecraftLocalization extends AutoCloseable {
    /**
     * Keyboard layouts that reverse searches over the loaded tables read queries against. Register a layout here once
     * and every index of every table loaded by this service uses it.
     */
    @NotNull
    KeyboardLayouts getLayouts();

    /** Returns known release versions in descending order. */
    @NotNull
    CompletionStage<List<MinecraftVersion>> availableVersions();

    /** Returns locale identifiers available for {@code version}. */
    @NotNull
    CompletionStage<List<String>> availableLocales(@NotNull MinecraftVersion version);

    /** Loads every locale declared by {@code request}, using valid cached data when possible. */
    @NotNull
    CompletionStage<TranslationBundle> load(@NotNull TranslationRequest request);

    /** Loads one {@code locale} for {@code version}. */
    @NotNull
    default CompletionStage<LocaleTranslations> locale(@NotNull MinecraftVersion version, @NotNull String locale) {
        return load(TranslationRequest.builder().version(version).locale(locale).build())
                .thenApply(bundle -> bundle.locale(locale));
    }

    /** Loads one typed {@code locale} for {@code version}. */
    @NotNull
    default CompletionStage<LocaleTranslations> locale(@NotNull MinecraftVersion version, @NotNull MinecraftLocale locale) {
        return locale(version, locale.getCode());
    }

    /** Forces validation and refresh of the translations declared by {@code request}. */
    @NotNull
    CompletionStage<TranslationBundle> refresh(@NotNull TranslationRequest request);

    /** Releases resources owned by this localization service. */
    @Override
    void close();

    /** Returns an empty localization-service builder. */
    @NotNull
    static Builder builder() {
        return new Builder();
    }

    /** Configures filesystem, timeout, cache, and execution limits. */
    final class Builder {
        private Path cacheDirectory;
        private Duration manifestTtl = Duration.ofHours(24);
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration readTimeout = Duration.ofSeconds(15);
        private int memoryEntries = 8;
        private int downloadConcurrency = 2;
        private ExecutorService executor;
        private KeyboardLayouts layouts = KeyboardLayouts.create();

        Builder() {
        }

        /** Sets the directory used for manifests, indexes, and locale caches. */
        @NotNull
        public Builder cacheDirectory(@NotNull Path value) {
            cacheDirectory = value;
            return this;
        }

        /** Sets how long version metadata may be reused without revalidation. */
        @NotNull
        public Builder manifestTtl(@NotNull Duration value) {
            if (value.isNegative()) {
                throw new IllegalArgumentException("Manifest TTL must not be negative");
            }
            manifestTtl = value;
            return this;
        }

        /** Sets the HTTP connection timeout. */
        @NotNull
        public Builder connectTimeout(@NotNull Duration value) {
            if (value.isNegative() || value.isZero()) {
                throw new IllegalArgumentException("Connect timeout must be positive");
            }
            connectTimeout = value;
            return this;
        }

        /** Sets the HTTP response read timeout. */
        @NotNull
        public Builder readTimeout(@NotNull Duration value) {
            if (value.isNegative() || value.isZero()) {
                throw new IllegalArgumentException("Read timeout must be positive");
            }
            readTimeout = value;
            return this;
        }

        /** Sets the maximum number of locale bundles retained in memory. */
        @NotNull
        public Builder memoryEntries(int value) {
            if (value <= 0) {
                throw new IllegalArgumentException("Memory entries must be positive");
            }
            memoryEntries = value;
            return this;
        }

        /** Sets the maximum number of concurrent network downloads. */
        @NotNull
        public Builder downloadConcurrency(int value) {
            if (value <= 0) {
                throw new IllegalArgumentException("Download concurrency must be positive");
            }
            downloadConcurrency = value;
            return this;
        }

        /** Supplies an externally owned executor; the service will not shut it down. */
        @NotNull
        public Builder executor(@NotNull ExecutorService value) {
            executor = value;
            return this;
        }

        /**
         * Supplies an externally owned layout registry; the service will not close it. Each service has its own by
         * default.
         */
        @NotNull
        public Builder layouts(@NotNull KeyboardLayouts value) {
            layouts = value;
            return this;
        }

        /** Validates the settings and creates a localization service. */
        @NotNull
        public MinecraftLocalization build() {
            if (cacheDirectory == null) {
                throw new IllegalArgumentException("Cache directory is required");
            }
            return new DefaultMinecraftLocalization(cacheDirectory, manifestTtl, connectTimeout, readTimeout,
                    memoryEntries, downloadConcurrency, executor, layouts);
        }
    }
}
