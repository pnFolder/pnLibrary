package org.pnfolder.pnlibrary.core.metrics;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics;
import org.pnfolder.pnlibrary.api.metrics.error.ErrorReporter;
import org.pnfolder.pnlibrary.api.metrics.error.TelemetryError;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsProvider;
import org.pnfolder.pnlibrary.core.metrics.factory.CompositePluginMetrics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CompositePluginMetricsTest {
    @Test
    void callerCannotRemoveOwnedBackendsOrMutateProviderMetadata() {
        List<String> calls = new ArrayList<>();
        List<PluginMetrics> source = new ArrayList<>(Arrays.<PluginMetrics>asList(
                new FakeMetrics(MetricsProvider.BSTATS, calls, () -> { }),
                new FakeMetrics(MetricsProvider.FASTSTATS, calls, () -> { })));
        CompositePluginMetrics composite = new CompositePluginMetrics(source);
        source.clear();
        assertThrows(UnsupportedOperationException.class, () -> composite.getProviders().clear());
        composite.close();
        assertEquals(Arrays.asList("faststats:close", "bstats:close"), calls);
    }

    @Test
    void closeReleasesEveryBackendEvenWhenOneBackendFails() {
        List<String> calls = new ArrayList<>();
        FakeMetrics first = new FakeMetrics(MetricsProvider.BSTATS, calls, () -> { });
        FakeMetrics second = new FakeMetrics(MetricsProvider.FASTSTATS, calls, () -> {
            throw new IllegalStateException("shutdown failed");
        });
        CompositePluginMetrics composite = new CompositePluginMetrics(Arrays.<PluginMetrics>asList(first, second));
        assertThrows(IllegalStateException.class, composite::close);
        assertEquals(Arrays.asList("faststats:close", "bstats:close"), calls);
        composite.close();
        assertEquals(Arrays.asList("faststats:close", "bstats:close"), calls);
    }

    @Test
    void compositeExposesTheErrorReporterOwnedByItsProvider() {
        ErrorReporter reporter = new ErrorReporter() {
            @Override
            public void capture(@NotNull TelemetryError error) {
            }

            @Override
            public void close() {
            }
        };
        FakeMetrics charts = new FakeMetrics(MetricsProvider.BSTATS, null, () -> { });
        PluginMetrics errors = new FakeMetrics(MetricsProvider.BSTATS, null, () -> { }) {
            @Nullable
            @Override
            public ErrorReporter getErrorReporter() {
                return reporter;
            }
        };
        assertEquals(reporter, new CompositePluginMetrics(Arrays.<PluginMetrics>asList(charts, errors)).getErrorReporter());
        assertNull(new CompositePluginMetrics(Arrays.<PluginMetrics>asList(charts)).getErrorReporter());
    }

    @Test
    void sharedChartsFanOutToEveryProviderAndCloseInReverseOrder() {
        List<String> calls = new ArrayList<>();
        CompositePluginMetrics composite = new CompositePluginMetrics(Arrays.<PluginMetrics>asList(
                new FakeMetrics(MetricsProvider.BSTATS, calls, () -> { }),
                new FakeMetrics(MetricsProvider.FASTSTATS, calls, () -> { })));

        composite.simplePie("platform", () -> "Paper");
        composite.close();

        Set<MetricsProvider> expected = EnumSet.of(MetricsProvider.BSTATS, MetricsProvider.FASTSTATS);
        assertEquals(expected, composite.getProviders());
        assertEquals(Arrays.asList("bstats:chart", "faststats:chart", "faststats:close", "bstats:close"), calls);
    }
}
