package org.pnfolder.pnlibrary.core.metrics.session;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.metrics.MetricsService;
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics;
import org.pnfolder.pnlibrary.api.metrics.error.ErrorReporter;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsProvider;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsProviderConfiguration;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.core.metrics.ErrorPipeline;
import org.pnfolder.pnlibrary.spi.metrics.PlatformMetricsFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Owns every platform metrics session and provides idempotent managed close handles. */
public final class MetricsSessions implements MetricsService, AutoCloseable {
    private final PlatformMetricsFactory factory;
    private final PlatformType platform;
    private final Set<PluginMetrics> sessions = Collections.newSetFromMap(new IdentityHashMap<PluginMetrics, Boolean>());
    private boolean closed;

    /** Sessions that open their configurations as given, without picking a platform's bStats project. */
    public MetricsSessions(@NotNull PlatformMetricsFactory factory) {
        this(factory, null);
    }

    /**
     * Creates the registry.
     *
     * @param factory opens the native sessions of the running platform
     * @param platform the running platform, used to pick its bStats project; {@code null} keeps configurations as given
     */
    public MetricsSessions(@NotNull PlatformMetricsFactory factory, @Nullable PlatformType platform) {
        this.factory = factory;
        this.platform = platform;
    }

    @Nullable
    @Override
    public synchronized ErrorReporter openErrorReporter(@NotNull Object owner,
                                                        @NotNull Collection<MetricsProviderConfiguration> configurations) {
        requireOpen();
        return factory.openErrorReporter(owner, forPlatform(configurations));
    }

    @NotNull
    @Override
    public synchronized PluginMetrics open(@NotNull Object owner,
                                           @NotNull Collection<MetricsProviderConfiguration> configurations) {
        requireOpen();
        if (!anyEnabled(configurations)) {
            throw new IllegalArgumentException("At least one metrics provider must be enabled");
        }
        return manage(factory.open(owner, forPlatform(configurations)));
    }

    /** One entry per open session: its providers, whether it has an error reporter and that reporter's counters. */
    @NotNull
    public synchronized List<Map<String, Object>> diagnosticSnapshot() {
        List<Map<String, Object>> snapshot = new ArrayList<>();
        for (PluginMetrics session : sessions) {
            ErrorReporter reporter = session.getErrorReporter();
            List<String> providers = new ArrayList<>();
            for (MetricsProvider provider : session.getProviders()) {
                providers.add(provider.name());
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("providers", providers);
            entry.put("errorReporter", reporter != null);
            entry.put("errorPipeline", reporter instanceof ErrorPipeline
                    ? ((ErrorPipeline) reporter).diagnosticSnapshot() : null);
            snapshot.add(entry);
        }
        return snapshot;
    }

    /** Counts of sessions, providers and error reporters for the diagnostics report. */
    @NotNull
    public synchronized Map<String, Object> diagnosticAnalytics() {
        List<Map<String, Object>> snapshots = diagnosticSnapshot();
        Map<String, Integer> providers = new LinkedHashMap<>();
        int reporters = 0;
        int pipelines = 0;
        for (Map<String, Object> snapshot : snapshots) {
            for (Object provider : (List<?>) snapshot.get("providers")) {
                Integer count = providers.get(provider.toString());
                providers.put(provider.toString(), count == null ? 1 : count + 1);
            }
            reporters += Boolean.TRUE.equals(snapshot.get("errorReporter")) ? 1 : 0;
            pipelines += snapshot.get("errorPipeline") instanceof Map ? 1 : 0;
        }
        Map<String, Object> analytics = new LinkedHashMap<>();
        analytics.put("sessionCount", snapshots.size());
        analytics.put("providers", providers);
        analytics.put("errorReporterCount", reporters);
        analytics.put("errorPipelineCount", pipelines);
        analytics.put("reportedErrors", reportedErrors(snapshots));
        return analytics;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (PluginMetrics session : new ArrayList<>(sessions)) {
            Failures.runCatchingNonFatal(() -> {
                session.close();
                return null;
            });
        }
        sessions.clear();
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Metrics service is closed");
        }
    }

    private static boolean anyEnabled(Collection<MetricsProviderConfiguration> configurations) {
        for (MetricsProviderConfiguration configuration : configurations) {
            if (configuration.isEnabled()) {
                return true;
            }
        }
        return false;
    }

    /** Picks the bStats project of the running platform; without a known platform the configurations stay as given. */
    private Collection<MetricsProviderConfiguration> forPlatform(Collection<MetricsProviderConfiguration> configurations) {
        if (platform == null) {
            return configurations;
        }
        List<MetricsProviderConfiguration> picked = new ArrayList<>();
        for (MetricsProviderConfiguration configuration : configurations) {
            picked.add(configuration.isEnabled() ? configuration.forPlatform(platform) : configuration);
        }
        return picked;
    }

    private PluginMetrics manage(PluginMetrics delegate) {
        ManagedMetrics managed = new ManagedMetrics(delegate, session -> {
            synchronized (this) {
                sessions.remove(session);
            }
        });
        sessions.add(managed);
        return managed;
    }

    // The snapshot has no "reportedErrors" key (the counter is called "captured"), so this has always been 0.
    private static long reportedErrors(List<Map<String, Object>> snapshots) {
        long total = 0L;
        for (Map<String, Object> snapshot : snapshots) {
            Object pipeline = snapshot.get("errorPipeline");
            Object count = pipeline instanceof Map ? ((Map<?, ?>) pipeline).get("reportedErrors") : null;
            total += count instanceof Number ? ((Number) count).longValue() : 0L;
        }
        return total;
    }
}
