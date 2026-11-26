package org.pnfolder.pnlibrary.api.metrics.provider;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics;
import org.pnfolder.pnlibrary.api.platform.PlatformType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Builds provider-neutral metrics configuration without exposing provider internals. */
public final class MetricsBuilder {
    private final List<MetricsProviderConfiguration> configurations = new ArrayList<>();
    private final List<Consumer<PluginMetrics>> configurers = new ArrayList<>();
    private final Map<PlatformType, Integer> bStatsByPlatform = new LinkedHashMap<>();
    private Integer bStatsDefault;
    private boolean enabled = true;

    /** Whether metrics start together with the module. */
    public boolean isEnabled() {
        return enabled;
    }

    /** Selects the initial state without leaving the metrics DSL. */
    @NotNull
    public MetricsBuilder enabled(boolean value) {
        enabled = value;
        return this;
    }

    /** Enables bStats for this module with one project used on every platform without its own ID. */
    @NotNull
    public MetricsBuilder bStats(int projectId) {
        requirePositive(projectId);
        if (bStatsDefault != null) {
            throw new IllegalStateException("Metrics provider " + MetricsProvider.BSTATS + " is already configured");
        }
        bStatsDefault = projectId;
        return this;
    }

    /** Enables bStats for this module with the project of one platform; bStats keeps a separate project per platform. */
    @NotNull
    public MetricsBuilder bStats(@NotNull PlatformType platform, int projectId) {
        requirePositive(projectId);
        if (bStatsByPlatform.containsKey(platform)) {
            throw new IllegalStateException("bStats project for " + platform.getDisplayName() + " is already configured");
        }
        bStatsByPlatform.put(platform, projectId);
        return this;
    }

    /** Enables bStats for this module with the projects of several platforms at once. */
    @NotNull
    public MetricsBuilder bStats(@NotNull Map<PlatformType, Integer> projectIds) {
        for (Map.Entry<PlatformType, Integer> entry : projectIds.entrySet()) {
            bStats(entry.getKey(), entry.getValue());
        }
        return this;
    }

    /** Enables FastStats for this module. */
    @NotNull
    public MetricsBuilder fastStats(@NotNull String token) {
        if (token.trim().isEmpty()) {
            throw new IllegalArgumentException("FastStats token must not be blank");
        }
        add(MetricsProviderConfiguration.fastStats(token));
        return this;
    }

    /** Registers charts for every enabled provider. */
    @NotNull
    public MetricsBuilder charts(@NotNull Consumer<PluginMetrics> configure) {
        configurers.add(configure);
        return this;
    }

    /** Returns the configured providers, rejecting an empty setup. */
    @NotNull
    public List<MetricsProviderConfiguration> build() {
        List<MetricsProviderConfiguration> result = new ArrayList<>();
        if (bStatsDefault != null || !bStatsByPlatform.isEmpty()) {
            result.add(new MetricsProviderConfiguration(MetricsProvider.BSTATS, true, bStatsDefault,
                    bStatsByPlatform, null));
        }
        if (configurations.isEmpty() && result.isEmpty()) {
            throw new IllegalArgumentException("At least one metrics provider is required");
        }
        result.addAll(configurations);
        return result;
    }

    /** Replays registered chart callbacks when a runtime session is created or restarted. */
    public void configure(@NotNull PluginMetrics metrics) {
        for (Consumer<PluginMetrics> configurer : configurers) {
            configurer.accept(metrics);
        }
    }

    private static void requirePositive(int projectId) {
        if (projectId <= 0) {
            throw new IllegalArgumentException("Metrics project ID must be positive");
        }
    }

    private void add(MetricsProviderConfiguration configuration) {
        for (MetricsProviderConfiguration existing : configurations) {
            if (existing.getProvider() == configuration.getProvider()) {
                throw new IllegalArgumentException("Metrics provider " + configuration.getProvider() + " is already configured");
            }
        }
        configurations.add(configuration);
    }
}
