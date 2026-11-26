package org.pnfolder.pnlibrary.api.diagnostics.report;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticConfiguration;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticsContributor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * A declarative diagnostics container that a pnFolder plugin registers with pnLibrary.
 *
 * <pre>{@code
 * DiagnosticContainer container = DiagnosticContainer.builder("auction")
 *     .snapshot(() -> snapshotOfAuctions())
 *     .configuration(DiagnosticConfiguration.builder("config.yml").exclude("storage.internalPool").build())
 *     .configuration("messages.yml")
 *     .build();
 * }</pre>
 */
public final class DiagnosticContainer implements DiagnosticsContributor {
    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9_.-]{1,96}");

    private final String id;
    private final Supplier<Map<String, Object>> snapshotProvider;
    private final List<DiagnosticConfiguration> configurations;

    private DiagnosticContainer(Builder builder) {
        if (!VALID_ID.matcher(builder.id).matches()) {
            throw new IllegalArgumentException("Invalid diagnostic container id: " + builder.id);
        }
        this.id = builder.id;
        final Map<String, Object> fixedData = new LinkedHashMap<>(builder.fixed);
        this.snapshotProvider = builder.snapshotProvider != null ? builder.snapshotProvider : () -> fixedData;
        this.configurations = Collections.unmodifiableList(new ArrayList<>(builder.configurations));
    }

    /**
     * Starts a diagnostics declaration identified by {@code id}.
     *
     * <p>Identifiers may contain ASCII letters, digits, dots, underscores, and hyphens. Validation occurs in
     * {@link Builder#build}.
     */
    @NotNull
    public static Builder builder(@NotNull String id) {
        return new Builder(id);
    }

    @NotNull
    @Override
    public String getId() {
        return id;
    }

    @NotNull
    @Override
    public Map<String, Object> collect() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(snapshotProvider.get()));
    }

    @NotNull
    @Override
    public Collection<String> configurationFiles() {
        List<String> paths = new ArrayList<>();
        for (DiagnosticConfiguration configuration : configurations) {
            paths.add(configuration.getPath());
        }
        return Collections.unmodifiableList(paths);
    }

    @NotNull
    @Override
    public Collection<DiagnosticConfiguration> configurations() {
        return configurations;
    }

    /** Fluent builder for one immutable {@link DiagnosticContainer}. */
    public static final class Builder {
        private final String id;
        private Supplier<Map<String, Object>> snapshotProvider;
        private final Map<String, Object> fixed = new LinkedHashMap<>();
        private final List<DiagnosticConfiguration> configurations = new ArrayList<>();

        private Builder(String id) {
            this.id = id;
        }

        /**
         * Replaces fixed values with a dynamic snapshot provider.
         *
         * <p>The provider is called once for every report collection and should return promptly. Exceptions are
         * handled by the diagnostics service and recorded as contributor failures.
         */
        @NotNull
        public Builder snapshot(@NotNull Supplier<Map<String, Object>> provider) {
            snapshotProvider = provider;
            return this;
        }

        /** Adds a fixed value shown in the diagnostics under {@code name}. */
        @NotNull
        public Builder value(@NotNull String name, @Nullable Object value) {
            fixed.put(name, value);
            return this;
        }

        /** Adds the entries of a configuration object to the diagnostics. */
        @NotNull
        public Builder configuration(@NotNull DiagnosticConfiguration config) {
            configurations.add(config);
            return this;
        }

        /** Adds the configuration file at {@code path} to the diagnostics. */
        @NotNull
        public Builder configuration(@NotNull String path) {
            configurations.add(DiagnosticConfiguration.builder(path).build());
            return this;
        }

        /**
         * Validates the contributor identifier and creates an immutable container.
         *
         * @throws IllegalArgumentException if the identifier is empty, longer than 96 characters, or contains
         *     unsupported characters
         */
        @NotNull
        public DiagnosticContainer build() {
            return new DiagnosticContainer(this);
        }
    }
}
