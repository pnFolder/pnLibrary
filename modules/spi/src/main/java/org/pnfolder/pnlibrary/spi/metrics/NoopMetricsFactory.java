package org.pnfolder.pnlibrary.spi.metrics;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsProvider;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsProviderConfiguration;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Metrics factory used by platforms without a native metrics adapter.
 *
 * <p>The returned session reports the enabled providers but discards all chart registrations.
 * This allows shared runtime code to remain branch-free when metrics are unavailable.
 */
public final class NoopMetricsFactory implements PlatformMetricsFactory {
    /** The only instance. */
    public static final NoopMetricsFactory INSTANCE = new NoopMetricsFactory();

    private NoopMetricsFactory() {
    }

    @NotNull
    @Override
    public PluginMetrics open(@NotNull Object owner, @NotNull Collection<MetricsProviderConfiguration> configurations) {
        Set<MetricsProvider> providers = new LinkedHashSet<>();
        for (MetricsProviderConfiguration configuration : configurations) {
            if (configuration.isEnabled()) {
                providers.add(configuration.getProvider());
            }
        }
        return new NoopMetrics(providers);
    }

    private static final class NoopMetrics implements PluginMetrics {
        private final Set<MetricsProvider> providers;

        NoopMetrics(Set<MetricsProvider> providers) {
            this.providers = providers;
        }

        @NotNull
        @Override
        public Set<MetricsProvider> getProviders() {
            return providers;
        }

        @NotNull
        @Override
        public PluginMetrics simplePie(@NotNull String id, @NotNull Supplier<String> value) {
            return this;
        }

        @NotNull
        @Override
        public PluginMetrics advancedPie(@NotNull String id, @NotNull Supplier<Map<String, Integer>> values) {
            return this;
        }

        @NotNull
        @Override
        public PluginMetrics drilldownPie(
                @NotNull String id, @NotNull Supplier<Map<String, Map<String, Integer>>> values) {
            return this;
        }

        @NotNull
        @Override
        public PluginMetrics singleLineChart(@NotNull String id, @NotNull Supplier<Integer> value) {
            return this;
        }

        @NotNull
        @Override
        public PluginMetrics multiLineChart(@NotNull String id, @NotNull Supplier<Map<String, Integer>> values) {
            return this;
        }

        @NotNull
        @Override
        public PluginMetrics simpleBarChart(@NotNull String id, @NotNull Supplier<Map<String, Integer>> values) {
            return this;
        }

        @NotNull
        @Override
        public PluginMetrics advancedBarChart(@NotNull String id, @NotNull Supplier<Map<String, int[]>> values) {
            return this;
        }

        @Override
        public void close() {
        }
    }
}
