package org.pnfolder.pnlibrary.core.metrics.session;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsCredentials;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsProvider;
import org.pnfolder.pnlibrary.core.metrics.ChartIds;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Provides fluent chart registration and lifecycle checks shared by metrics providers.
 *
 * <p>Suppliers are retained without evaluation. Providers translate a {@link Chart} into their native
 * format and invoke suppliers only when collecting samples.
 */
public abstract class AbstractMetricsSession implements PluginMetrics {
    private final MetricsCredentials<?> credentials;
    private final Set<MetricsProvider> providerSet;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /** @param credentials credentials of the provider this session reports to */
    protected AbstractMetricsSession(@NotNull MetricsCredentials<?> credentials) {
        this.credentials = Objects.requireNonNull(credentials, "credentials");
        this.providerSet = Collections.singleton(credentials.getProvider());
    }

    /** Credentials of the provider this session reports to. */
    @NotNull
    public final MetricsCredentials<?> getCredentials() {
        return credentials;
    }

    @NotNull
    @Override
    public final Set<MetricsProvider> getProviders() {
        return providerSet;
    }

    @NotNull
    @Override
    public final PluginMetrics simplePie(@NotNull String id, @NotNull Supplier<String> value) {
        return add(new Chart.SimplePie(ChartIds.validChartId(id), value));
    }

    @NotNull
    @Override
    public final PluginMetrics advancedPie(@NotNull String id, @NotNull Supplier<Map<String, Integer>> values) {
        return add(new Chart.AdvancedPie(ChartIds.validChartId(id), values));
    }

    @NotNull
    @Override
    public final PluginMetrics drilldownPie(@NotNull String id,
                                            @NotNull Supplier<Map<String, Map<String, Integer>>> values) {
        return add(new Chart.DrilldownPie(ChartIds.validChartId(id), values));
    }

    @NotNull
    @Override
    public final PluginMetrics singleLineChart(@NotNull String id, @NotNull Supplier<Integer> value) {
        return add(new Chart.SingleLine(ChartIds.validChartId(id), value));
    }

    @NotNull
    @Override
    public final PluginMetrics multiLineChart(@NotNull String id, @NotNull Supplier<Map<String, Integer>> values) {
        return add(new Chart.MultiLine(ChartIds.validChartId(id), values));
    }

    @NotNull
    @Override
    public final PluginMetrics simpleBarChart(@NotNull String id, @NotNull Supplier<Map<String, Integer>> values) {
        return add(new Chart.SimpleBar(ChartIds.validChartId(id), values));
    }

    @NotNull
    @Override
    public final PluginMetrics advancedBarChart(@NotNull String id, @NotNull Supplier<Map<String, int[]>> values) {
        return add(new Chart.AdvancedBar(ChartIds.validChartId(id), values));
    }

    /** Releases provider resources at most once and prevents further registration. */
    @Override
    public final void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                shutdown();
            } finally {
                credentials.close();
            }
        }
    }

    /** Whether this session has already released its provider resources. */
    protected final boolean isClosed() {
        return closed.get();
    }

    /** Hands a validated chart to the provider. */
    protected abstract void register(@NotNull Chart chart);

    /** Releases resources owned by the provider. Called only once. */
    protected abstract void shutdown();

    private PluginMetrics add(Chart chart) {
        if (isClosed()) {
            throw new IllegalStateException("Metrics session is closed");
        }
        register(chart);
        return this;
    }
}
