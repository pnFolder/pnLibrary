package org.pnfolder.pnlibrary.core.metrics;

import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsProviderConfiguration;
import org.pnfolder.pnlibrary.core.metrics.factory.NativePluginMetricsFactory;
import org.pnfolder.pnlibrary.spi.metrics.NoopMetricsFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NativePluginMetricsFactoryTest {
    private static final class NativePlugin {
    }

    private final List<String> calls = new ArrayList<>();
    private final List<MetricsProviderConfiguration> providers = Arrays.asList(
            MetricsProviderConfiguration.bStats(7), MetricsProviderConfiguration.fastStats("token"));
    private final NativePluginMetricsFactory<NativePlugin> factory = new NativePluginMetricsFactory<>(
            NativePlugin.class, "Test",
            (plugin, projectId) -> {
                calls.add("bstats:" + projectId);
                return session();
            },
            (plugin, token) -> {
                calls.add("faststats:" + token);
                return session();
            });

    @Test
    void nativePluginOwnersOpenEveryEnabledProvider() {
        factory.open(new NativePlugin(), providers);
        assertEquals(Arrays.asList("bstats:7", "faststats:token"), calls);
    }

    @Test
    void otherOwnersAreRejectedBeforeAProviderIsOpened() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> factory.open("owner", providers));
        assertEquals("Test metrics owner must be a Test plugin; received java.lang.String", error.getMessage());
        assertEquals(0, calls.size());
    }

    private PluginMetrics session() {
        return NoopMetricsFactory.INSTANCE.open(new Object(), providers);
    }
}
