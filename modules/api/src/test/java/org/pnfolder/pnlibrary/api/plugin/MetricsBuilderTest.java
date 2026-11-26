package org.pnfolder.pnlibrary.api.plugin;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsBuilder;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsProvider;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsProviderConfiguration;
import org.pnfolder.pnlibrary.api.platform.PlatformType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MetricsBuilderTest {
    @Test
    void buildsIndependentBStatsAndFastStatsConfigurations() {
        MetricsBuilder builder = new MetricsBuilder();
        builder.bStats(32592);
        builder.fastStats("token");
        List<MetricsProviderConfiguration> configurations = builder.build();

        assertEquals(Arrays.asList(MetricsProvider.BSTATS, MetricsProvider.FASTSTATS),
                Arrays.asList(configurations.get(0).getProvider(), configurations.get(1).getProvider()));
        assertEquals(Integer.valueOf(32592), configurations.get(0).getProjectId());
        assertEquals("token", configurations.get(1).getToken());
    }

    @Test
    void picksTheBStatsProjectOfTheRunningPlatform() {
        List<MetricsProviderConfiguration> built = new MetricsBuilder()
                .bStats(PlatformType.BUKKIT, 1)
                .bStats(Collections.singletonMap(PlatformType.VELOCITY, 3))
                .bStats(10)
                .build();
        assertEquals(1, built.size());
        MetricsProviderConfiguration configuration = built.get(0);

        assertEquals(Integer.valueOf(1), configuration.forPlatform(PlatformType.BUKKIT).getProjectId());
        assertEquals(Integer.valueOf(3), configuration.forPlatform(PlatformType.VELOCITY).getProjectId());
        assertEquals(Integer.valueOf(10), configuration.forPlatform(PlatformType.BUNGEECORD).getProjectId());
    }

    @Test
    void aPlatformWithoutABStatsProjectIsRejected() {
        MetricsProviderConfiguration configuration = new MetricsBuilder().bStats(PlatformType.BUKKIT, 1).build().get(0);

        assertThrows(IllegalArgumentException.class, () -> configuration.forPlatform(PlatformType.VELOCITY));
    }

    @Test
    void rejectsARepeatedBStatsProjectForOnePlatform() {
        MetricsBuilder builder = new MetricsBuilder().bStats(PlatformType.BUKKIT, 1);

        assertThrows(IllegalStateException.class, () -> builder.bStats(PlatformType.BUKKIT, 2));
    }

    @Test
    void rejectsEmptyMetricsConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new MetricsBuilder().build());
    }

    @Test
    void keepsInitialStateAndChartsInsideTheMetricsDsl() {
        boolean[] configured = {false};
        MetricsBuilder builder = new MetricsBuilder()
                .bStats(32592)
                .enabled(false)
                .charts(metrics -> configured[0] = true);

        assertFalse(builder.isEnabled());
        builder.configure(new NoCharts());
        assertEquals(true, configured[0]);
    }

    private static final class NoCharts implements PluginMetrics {
        @NotNull
        @Override
        public Set<MetricsProvider> getProviders() {
            return Collections.singleton(MetricsProvider.BSTATS);
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
        public PluginMetrics drilldownPie(@NotNull String id, @NotNull Supplier<Map<String, Map<String, Integer>>> values) {
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
