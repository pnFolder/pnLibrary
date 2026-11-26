package org.pnfolder.pnlibrary.core.plugin;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.metrics.MetricsService;
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsProvider;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsProviderConfiguration;

/** A metrics service that notes the project id it was asked for. */
public final class MetricsRecorder implements MetricsService {
    public Integer lastProjectId;

    @Override
    public @NotNull PluginMetrics open(@NotNull Object owner, @NotNull Collection<MetricsProviderConfiguration> configurations) {
        lastProjectId = null;
        Set<MetricsProvider> providers = new LinkedHashSet<>();
        for (MetricsProviderConfiguration configuration : configurations) {
            if (lastProjectId == null) {
                lastProjectId = configuration.getProjectId();
            }
            providers.add(configuration.getProvider());
        }
        return new NoopMetrics(providers);
    }

    private static final class NoopMetrics implements PluginMetrics {
        private final Set<MetricsProvider> providers;

        private NoopMetrics(Set<MetricsProvider> providers) {
            this.providers = providers;
        }

        @Override
        public @NotNull Set<MetricsProvider> getProviders() {
            return providers;
        }

        @Override
        public @NotNull PluginMetrics simplePie(@NotNull String id, @NotNull Supplier<String> value) {
            return this;
        }

        @Override
        public @NotNull PluginMetrics advancedPie(@NotNull String id, @NotNull Supplier<Map<String, Integer>> values) {
            return this;
        }

        @Override
        public @NotNull PluginMetrics drilldownPie(@NotNull String id, @NotNull Supplier<Map<String, Map<String, Integer>>> values) {
            return this;
        }

        @Override
        public @NotNull PluginMetrics singleLineChart(@NotNull String id, @NotNull Supplier<Integer> value) {
            return this;
        }

        @Override
        public @NotNull PluginMetrics multiLineChart(@NotNull String id, @NotNull Supplier<Map<String, Integer>> values) {
            return this;
        }

        @Override
        public @NotNull PluginMetrics simpleBarChart(@NotNull String id, @NotNull Supplier<Map<String, Integer>> values) {
            return this;
        }

        @Override
        public @NotNull PluginMetrics advancedBarChart(@NotNull String id, @NotNull Supplier<Map<String, int[]>> values) {
            return this;
        }

        @Override
        public void close() {
        }
    }
}
