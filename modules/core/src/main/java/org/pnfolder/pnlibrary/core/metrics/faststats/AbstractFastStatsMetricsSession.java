package org.pnfolder.pnlibrary.core.metrics.faststats;

import dev.faststats.Metrics;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsCredentials;
import org.pnfolder.pnlibrary.core.metrics.session.AbstractMetricsSession;
import org.pnfolder.pnlibrary.core.metrics.session.Chart;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Translates shared charts to FastStats metrics and owns the platform context.
 *
 * <p>Configure charts before calling {@link #start()}. The context starts submission only after the
 * configured metrics have been registered; error reporting remains available through this session.
 */
public abstract class AbstractFastStatsMetricsSession extends AbstractMetricsSession {
    private final MetricsCredentials.FastStats fastStatsCredentials;
    private final FastStatsContextFactory contextFactory;
    private final FastStatsErrorReporter reporter = new FastStatsErrorReporter();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final List<Chart> pending = new ArrayList<>();
    private FastStatsContext context;

    /** Accepts the legacy String API without retaining the String in this session. */
    protected AbstractFastStatsMetricsSession(@NotNull String token, @NotNull FastStatsContextFactory contextFactory) {
        this(copyOf(token), contextFactory);
    }

    /** Copies caller-owned token characters; the caller remains responsible for erasing its array. */
    protected AbstractFastStatsMetricsSession(@NotNull char[] token, @NotNull FastStatsContextFactory contextFactory) {
        this(new MetricsCredentials.FastStats(token), contextFactory);
    }

    /** Uses credentials this session then owns and closes. */
    protected AbstractFastStatsMetricsSession(@NotNull MetricsCredentials.FastStats credentials,
                                              @NotNull FastStatsContextFactory contextFactory) {
        super(credentials);
        this.fastStatsCredentials = credentials;
        this.contextFactory = contextFactory;
    }

    @NotNull
    @Override
    public final FastStatsErrorReporter getErrorReporter() {
        return reporter;
    }

    /** Retains a lazy chart until the native FastStats factory is initialized. */
    @Override
    protected void register(@NotNull Chart chart) {
        if (started.get()) {
            throw new IllegalStateException("FastStats metrics must be configured before the session starts");
        }
        pending.add(chart);
    }

    /** Initializes and starts native submission after chart configuration. */
    @Override
    public void start() {
        if (isClosed() || !started.compareAndSet(false, true)) {
            return;
        }
        context = fastStatsCredentials.useToken(token ->
                contextFactory.create(token, reporter.getTracker(), factory -> {
                    for (Chart chart : pending) {
                        registerNative(factory, chart);
                    }
                    return factory.create();
                }));
        context.ready();
    }

    @Override
    protected void shutdown() {
        if (context != null) {
            context.shutdown();
        }
        pending.clear();
    }

    /** Maps chart shapes to the metric types supported by FastStats. */
    private static void registerNative(Metrics.Factory factory, Chart chart) {
        String id = chart.getId();
        if (chart instanceof Chart.SimplePie) {
            FastStatsSupport.simplePie(factory, id, ((Chart.SimplePie) chart).getValue());
        } else if (chart instanceof Chart.AdvancedPie) {
            FastStatsSupport.numberMap(factory, id, ((Chart.AdvancedPie) chart).getValues());
        } else if (chart instanceof Chart.DrilldownPie) {
            FastStatsSupport.drilldownPie(factory, id, ((Chart.DrilldownPie) chart).getValues());
        } else if (chart instanceof Chart.SingleLine) {
            FastStatsSupport.singleLineChart(factory, id, ((Chart.SingleLine) chart).getValue());
        } else if (chart instanceof Chart.MultiLine) {
            FastStatsSupport.numberMap(factory, id, ((Chart.MultiLine) chart).getValues());
        } else if (chart instanceof Chart.SimpleBar) {
            FastStatsSupport.numberMap(factory, id, ((Chart.SimpleBar) chart).getValues());
        } else if (chart instanceof Chart.AdvancedBar) {
            FastStatsSupport.advancedBarChart(factory, id, ((Chart.AdvancedBar) chart).getValues());
        } else {
            throw new IllegalArgumentException("Unsupported chart: " + chart);
        }
    }

    /** Copies a legacy String token into owned credentials and erases the intermediate buffer. */
    private static MetricsCredentials.FastStats copyOf(String token) {
        char[] characters = token.toCharArray();
        try {
            return new MetricsCredentials.FastStats(characters);
        } finally {
            Arrays.fill(characters, '\u0000');
        }
    }
}
