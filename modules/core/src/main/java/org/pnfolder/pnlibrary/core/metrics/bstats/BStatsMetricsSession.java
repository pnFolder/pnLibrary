package org.pnfolder.pnlibrary.core.metrics.bstats;

import org.bstats.charts.AdvancedBarChart;
import org.bstats.charts.AdvancedPie;
import org.bstats.charts.CustomChart;
import org.bstats.charts.DrilldownPie;
import org.bstats.charts.MultiLineChart;
import org.bstats.charts.SimpleBarChart;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsCredentials;
import org.pnfolder.pnlibrary.core.metrics.session.AbstractMetricsSession;
import org.pnfolder.pnlibrary.core.metrics.session.Chart;

import java.util.function.Consumer;

/** Translates shared lazy chart definitions into native bStats charts. */
public final class BStatsMetricsSession extends AbstractMetricsSession {
    private final Consumer<CustomChart> addChart;
    private final Runnable shutdown;

    /**
     * Creates a session.
     *
     * @param projectId bStats project the charts belong to
     * @param addChart hands a native chart to the platform client
     * @param shutdown stops the platform client
     */
    public BStatsMetricsSession(int projectId, @NotNull Consumer<CustomChart> addChart, @NotNull Runnable shutdown) {
        super(new MetricsCredentials.BStats(projectId));
        this.addChart = addChart;
        this.shutdown = shutdown;
    }

    /** Registers the matching native chart without evaluating its supplier. */
    @Override
    protected void register(@NotNull Chart chart) {
        addChart.accept(nativeChart(chart));
    }

    @Override
    protected void shutdown() {
        shutdown.run();
    }

    private static CustomChart nativeChart(Chart chart) {
        String id = chart.getId();
        if (chart instanceof Chart.SimplePie) {
            return new SimplePie(id, ((Chart.SimplePie) chart).getValue()::get);
        }
        if (chart instanceof Chart.AdvancedPie) {
            return new AdvancedPie(id, ((Chart.AdvancedPie) chart).getValues()::get);
        }
        if (chart instanceof Chart.DrilldownPie) {
            return new DrilldownPie(id, ((Chart.DrilldownPie) chart).getValues()::get);
        }
        if (chart instanceof Chart.SingleLine) {
            return new SingleLineChart(id, ((Chart.SingleLine) chart).getValue()::get);
        }
        if (chart instanceof Chart.MultiLine) {
            return new MultiLineChart(id, ((Chart.MultiLine) chart).getValues()::get);
        }
        if (chart instanceof Chart.SimpleBar) {
            return new SimpleBarChart(id, ((Chart.SimpleBar) chart).getValues()::get);
        }
        if (chart instanceof Chart.AdvancedBar) {
            return new AdvancedBarChart(id, ((Chart.AdvancedBar) chart).getValues()::get);
        }
        throw new IllegalArgumentException("Unsupported chart: " + chart);
    }
}
