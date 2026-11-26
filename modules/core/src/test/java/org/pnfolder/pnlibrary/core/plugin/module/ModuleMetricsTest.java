package org.pnfolder.pnlibrary.core.plugin.module;

import org.pnfolder.pnlibrary.core.plugin.module.definition.ModuleMetricsDefinition;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.metrics.MetricsService;
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics;
import org.pnfolder.pnlibrary.api.metrics.error.ErrorReporter;
import org.pnfolder.pnlibrary.api.metrics.error.TelemetryError;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsProvider;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsProviderConfiguration;

class ModuleMetricsTest {
    private static MetricsProviderConfiguration bStats(int projectId) {
        return MetricsProviderConfiguration.bStats(projectId);
    }

    private static MetricsProviderConfiguration fastStats() {
        return MetricsProviderConfiguration.fastStats("test-token");
    }

    private static ModuleMetricsDefinition definition(boolean enabled, List<Consumer<PluginMetrics>> configurers) {
        return new ModuleMetricsDefinition(Collections.singletonList(bStats(10)), enabled, configurers);
    }

    private static ModuleMetricsDefinition definition() {
        return definition(true, Collections.emptyList());
    }

    @Test
    void addingBStatsToAFastStatsOnlyConfigurationOpensBothProviders() {
        RecordingMetricsService service = new RecordingMetricsService(null);
        ModuleMetrics controller = new ModuleMetrics(new Object(), service,
            new ModuleMetricsDefinition(Collections.singletonList(fastStats()), false, Collections.emptyList()));

        controller.update(bStats(10));
        controller.enable();

        assertEquals(EnumSet.of(MetricsProvider.BSTATS, MetricsProvider.FASTSTATS), service.opened.get(0).providers);
        assertEquals(1, service.opened.size());
        controller.close();
    }

    @Test
    void errorReporterFollowsEnabledSessionLifecycle() {
        ErrorReporter reporter = new ErrorReporter() {
            @Override
            public void capture(@NotNull TelemetryError error) {
            }

            @Override
            public void close() {
            }
        };
        RecordingMetricsService service = new RecordingMetricsService(reporter);
        ModuleMetrics controller = new ModuleMetrics(new Object(), service, definition(), null);
        assertEquals(reporter, controller.errorReporterOrNull());
        controller.disable();
        assertNull(controller.errorReporterOrNull());
        controller.enable();
        assertEquals(reporter, controller.errorReporterOrNull());
        controller.close();
        assertNull(controller.errorReporterOrNull());
    }

    @Test
    void runtimeChartConfigurationRestartsSessionAndReplaysAllCharts() {
        RecordingMetricsService service = new RecordingMetricsService(null);
        ModuleMetrics controller = new ModuleMetrics(new Object(), service, definition(), null);

        controller.configure(metrics -> metrics.simplePie("mode", () -> "test"));
        controller.configure(metrics -> metrics.simplePie("database", () -> "mysql"));

        assertTrue(service.opened.get(0).closed);
        assertEquals(Arrays.asList("mode", "database"), service.opened.get(service.opened.size() - 1).charts);
        controller.disable();
        assertNull(controller.errorReporterOrNull());
        controller.close();
    }

    @Test
    void metricsCanBeDisabledEnabledAndMovedToAnotherProviderSetup() {
        RecordingMetricsService service = new RecordingMetricsService(null);
        Consumer<PluginMetrics> mode = metrics -> metrics.simplePie("mode", () -> "test");
        ModuleMetrics controller = new ModuleMetrics(new Object(), service, definition(true, Collections.singletonList(mode)));

        assertTrue(controller.isEnabled());
        assertEquals(Arrays.asList(Arrays.asList(10)), service.requestedProjectIds());
        assertEquals(Arrays.asList("mode"), service.opened.get(0).charts);

        controller.disable();
        assertFalse(controller.isEnabled());
        assertTrue(service.opened.get(0).closed);
        assertEquals(Arrays.asList(bStats(10)), controller.getProviderConfigurations());

        controller.enable();
        assertTrue(controller.isEnabled());
        assertEquals(Arrays.asList(Arrays.asList(10), Arrays.asList(10)), service.requestedProjectIds());

        controller.update(bStats(20));
        assertEquals(Arrays.asList(bStats(20)), controller.getProviderConfigurations());
        assertTrue(controller.isEnabled());
        assertEquals(Arrays.asList(Arrays.asList(10), Arrays.asList(10), Arrays.asList(20)), service.requestedProjectIds());
        assertTrue(service.opened.get(1).closed);
        assertEquals(Arrays.asList("mode"), service.opened.get(2).charts);
    }

    @Test
    void unchangedProviderConfigurationDoesNotRestartTheSession() {
        RecordingMetricsService service = new RecordingMetricsService(null);
        ModuleMetrics controller = new ModuleMetrics(new Object(), service, definition(), null);

        controller.update(bStats(10));

        assertEquals(1, service.opened.size());
        assertFalse(service.opened.get(0).closed);
    }

    @Test
    void enablingWithoutAnEnabledProviderIsRejected() {
        ModuleMetrics controller = new ModuleMetrics(new Object(), new RecordingMetricsService(null),
            new ModuleMetricsDefinition(Collections.emptyList(), false,
                Collections.emptyList()));

        assertThrows(IllegalArgumentException.class, controller::enable);
        assertFalse(controller.isEnabled());
    }

    @Test
    void closedControllerRejectsMutation() {
        ModuleMetrics controller = new ModuleMetrics(new Object(), new RecordingMetricsService(null),
            definition(false, Collections.emptyList()), null);
        controller.close();

        assertTrue(controller.isClosed());
        assertThrows(IllegalStateException.class, controller::enable);
        assertThrows(IllegalStateException.class, () -> controller.update(bStats(20)));
    }

    private static final class RecordingMetricsService implements MetricsService {
        private final ErrorReporter reporter;
        private final List<RecordingMetrics> opened = new ArrayList<>();
        private final List<List<MetricsProviderConfiguration>> requests = new ArrayList<>();

        private RecordingMetricsService(ErrorReporter reporter) {
            this.reporter = reporter;
        }

        @Override
        public @NotNull PluginMetrics open(@NotNull Object owner, @NotNull Collection<MetricsProviderConfiguration> configurations) {
            requests.add(new ArrayList<>(configurations));
            Set<MetricsProvider> providers = new LinkedHashSet<>();
            for (MetricsProviderConfiguration configuration : configurations) {
                providers.add(configuration.getProvider());
            }
            RecordingMetrics metrics = new RecordingMetrics(providers, reporter);
            opened.add(metrics);
            return metrics;
        }

        private List<List<Integer>> requestedProjectIds() {
            List<List<Integer>> ids = new ArrayList<>();
            for (List<MetricsProviderConfiguration> request : requests) {
                List<Integer> projectIds = new ArrayList<>();
                for (MetricsProviderConfiguration configuration : request) {
                    if (configuration.getProjectId() != null) {
                        projectIds.add(configuration.getProjectId());
                    }
                }
                ids.add(projectIds);
            }
            return ids;
        }
    }

    private static final class RecordingMetrics implements PluginMetrics {
        private final Set<MetricsProvider> providers;
        private final ErrorReporter reporter;
        private final List<String> charts = new ArrayList<>();
        private boolean closed;

        private RecordingMetrics(Set<MetricsProvider> providers, ErrorReporter reporter) {
            this.providers = providers;
            this.reporter = reporter;
        }

        @Override
        public @NotNull Set<MetricsProvider> getProviders() {
            return providers;
        }

        @Override
        public ErrorReporter getErrorReporter() {
            return reporter;
        }

        @Override
        public @NotNull PluginMetrics simplePie(@NotNull String id, @NotNull Supplier<String> value) {
            charts.add(id);
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
            closed = true;
        }
    }
}
