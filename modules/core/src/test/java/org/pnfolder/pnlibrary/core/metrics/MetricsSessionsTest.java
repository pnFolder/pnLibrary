package org.pnfolder.pnlibrary.core.metrics;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics;
import org.pnfolder.pnlibrary.api.metrics.error.ErrorReporter;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsProvider;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsProviderConfiguration;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.core.metrics.session.MetricsSessions;
import org.pnfolder.pnlibrary.spi.metrics.PlatformMetricsFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricsSessionsTest {
    private final List<MetricsProviderConfiguration> bStats =
            Collections.singletonList(MetricsProviderConfiguration.bStats(123));
    private final List<MetricsProviderConfiguration> fastStats =
            Collections.singletonList(MetricsProviderConfiguration.fastStats("token"));

    private static PlatformMetricsFactory opening(Runnable onOpen, Runnable onClose) {
        return new PlatformMetricsFactory() {
            @NotNull
            @Override
            public PluginMetrics open(@NotNull Object owner, @NotNull Collection<MetricsProviderConfiguration> configurations) {
                onOpen.run();
                return FakeMetrics.closing(onClose);
            }
        };
    }

    @Test
    void opensTheBStatsProjectOfTheRunningPlatform() {
        AtomicReference<Integer> projectId = new AtomicReference<>();
        MetricsSessions registry = new MetricsSessions(new PlatformMetricsFactory() {
            @NotNull
            @Override
            public PluginMetrics open(@NotNull Object owner, @NotNull Collection<MetricsProviderConfiguration> configurations) {
                projectId.set(configurations.iterator().next().getProjectId());
                return FakeMetrics.closing(() -> { });
            }
        }, PlatformType.VELOCITY);
        registry.open(new Object(), Collections.singletonList(new MetricsProviderConfiguration(
                MetricsProvider.BSTATS, true, 1, Collections.singletonMap(PlatformType.VELOCITY, 3), null)));
        assertEquals(3, projectId.get());
        registry.close();
    }

    @Test
    void closedRegistryRejectsEveryMetricsFactoryEntryPoint() {
        AtomicInteger opened = new AtomicInteger();
        MetricsSessions registry = new MetricsSessions(new PlatformMetricsFactory() {
            @NotNull
            @Override
            public PluginMetrics open(@NotNull Object owner, @NotNull Collection<MetricsProviderConfiguration> configurations) {
                opened.incrementAndGet();
                return FakeMetrics.closing(() -> { });
            }

            @Nullable
            @Override
            public ErrorReporter openErrorReporter(@NotNull Object owner,
                                                   @NotNull Collection<MetricsProviderConfiguration> configurations) {
                opened.incrementAndGet();
                return null;
            }
        });
        registry.close();
        assertThrows(IllegalStateException.class, () -> registry.open(new Object(), bStats));
        assertThrows(IllegalStateException.class, () -> registry.open(new Object(), fastStats));
        assertThrows(IllegalStateException.class, () -> registry.openErrorReporter(new Object(), fastStats));
        assertEquals(0, opened.get());
        registry.close();
    }

    @Test
    void anEnabledProviderIsRequiredAndRuntimeClosesSessions() {
        AtomicInteger closed = new AtomicInteger();
        MetricsSessions registry = new MetricsSessions(opening(() -> { }, closed::incrementAndGet));

        assertThrows(IllegalArgumentException.class,
                () -> registry.open(new Object(), Collections.emptyList()));
        List<MetricsProviderConfiguration> disabled = new ArrayList<>();
        for (MetricsProviderConfiguration configuration : bStats) {
            disabled.add(configuration.withEnabled(false));
        }
        assertThrows(IllegalArgumentException.class, () -> registry.open(new Object(), disabled));
        PluginMetrics first = registry.open(new Object(), bStats);
        registry.open(new Object(), fastStats);
        first.simplePie("storage_type", () -> "sqlite");
        first.close();
        registry.close();

        assertEquals(2, closed.get());
    }

    @Test
    void managedMetricsClosesDelegateOnlyOnceUnderConcurrentClose() throws Exception {
        AtomicInteger closed = new AtomicInteger();
        MetricsSessions registry = new MetricsSessions(opening(() -> { }, closed::incrementAndGet));
        PluginMetrics metrics = registry.open(new Object(), bStats);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            for (int index = 0; index < 64; index++) {
                executor.submit(metrics::close);
            }
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }

        registry.close();
        assertEquals(1, closed.get());
    }

    @Test
    void providerConfigurationIsDelegatedAndManaged() {
        AtomicReference<List<MetricsProviderConfiguration>> received = new AtomicReference<>();
        AtomicInteger closed = new AtomicInteger();
        MetricsSessions registry = new MetricsSessions(new PlatformMetricsFactory() {
            @NotNull
            @Override
            public PluginMetrics open(@NotNull Object owner, @NotNull Collection<MetricsProviderConfiguration> configurations) {
                received.set(new ArrayList<>(configurations));
                return FakeMetrics.closing(closed::incrementAndGet);
            }
        });

        registry.open(new Object(), fastStats);
        registry.close();

        assertEquals(fastStats, received.get());
        assertEquals(1, closed.get());
    }
}
