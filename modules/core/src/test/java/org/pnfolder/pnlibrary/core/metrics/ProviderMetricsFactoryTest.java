package org.pnfolder.pnlibrary.core.metrics;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsProvider;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsProviderConfiguration;
import org.pnfolder.pnlibrary.core.metrics.factory.CompositePluginMetrics;
import org.pnfolder.pnlibrary.core.metrics.factory.ProviderMetricsFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderMetricsFactoryTest {
    private final MetricsProviderConfiguration bStats = MetricsProviderConfiguration.bStats(7);
    private final MetricsProviderConfiguration fastStats = MetricsProviderConfiguration.fastStats("token");

    @Test
    void singleEnabledProviderIsReturnedWithoutACompositeWrapper() {
        RecordingFactory factory = new RecordingFactory(false);
        PluginMetrics session = factory.open(new Object(), Arrays.asList(bStats, fastStats.withEnabled(false)));
        assertSame(factory.sessions.get(0), session);
        assertEquals(Arrays.asList("bstats:7"), factory.calls);
    }

    @Test
    void severalEnabledProvidersAreComposed() {
        RecordingFactory factory = new RecordingFactory(false);
        PluginMetrics session = factory.open(new Object(), Arrays.asList(bStats, fastStats));
        assertTrue(session instanceof CompositePluginMetrics);
        assertEquals(Arrays.asList("bstats:7", "faststats:token"), factory.calls);
    }

    @Test
    void alreadyOpenedProvidersAreClosedWhenALaterProviderFails() {
        RecordingFactory factory = new RecordingFactory(true);
        assertThrows(IllegalStateException.class, () -> factory.open(new Object(), Arrays.asList(bStats, fastStats)));
        assertEquals(Arrays.asList("close"), factory.closed);
    }

    @Test
    void missingCredentialsAndEmptySelectionsAreRejected() {
        RecordingFactory factory = new RecordingFactory(false);
        assertThrows(IllegalArgumentException.class, () -> factory.open(new Object(),
                Arrays.asList(new MetricsProviderConfiguration(MetricsProvider.BSTATS))));
        assertThrows(IllegalArgumentException.class,
                () -> factory.open(new Object(), Arrays.asList(bStats.withEnabled(false))));
    }

    private static final class RecordingFactory extends ProviderMetricsFactory {
        private final boolean failFastStats;
        final List<String> calls = new ArrayList<>();
        final List<String> closed = new ArrayList<>();
        final List<PluginMetrics> sessions = new ArrayList<>();

        RecordingFactory(boolean failFastStats) {
            this.failFastStats = failFastStats;
        }

        @NotNull
        @Override
        protected PluginMetrics openBStats(@NotNull Object owner, int projectId) {
            calls.add("bstats:" + projectId);
            return session();
        }

        @NotNull
        @Override
        protected PluginMetrics openFastStats(@NotNull Object owner, @NotNull String token) {
            calls.add("faststats:" + token);
            if (failFastStats) {
                throw new IllegalStateException("FastStats unavailable");
            }
            return session();
        }

        private PluginMetrics session() {
            PluginMetrics session = FakeMetrics.closing(() -> closed.add("close"));
            sessions.add(session);
            return session;
        }
    }
}
