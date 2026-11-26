package org.pnfolder.pnlibrary.core.metrics.factory;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsProviderConfiguration;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.spi.metrics.PlatformMetricsFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Base class for platform metrics factories.
 *
 * <p>Platforms only describe how to open one native bStats or FastStats session; provider selection,
 * composition of several enabled providers and rollback on partial failure live here.
 */
public abstract class ProviderMetricsFactory implements PlatformMetricsFactory {

    /** Opens the native bStats session of {@code owner} for a project. */
    @NotNull
    protected abstract PluginMetrics openBStats(@NotNull Object owner, int projectId);

    /** Opens the native FastStats session of {@code owner} for a project token. */
    @NotNull
    protected abstract PluginMetrics openFastStats(@NotNull Object owner, @NotNull String token);

    /** Opens every configured provider and returns one session that reports to all of them. */
    @NotNull
    @Override
    public final PluginMetrics open(@NotNull Object owner, @NotNull Collection<MetricsProviderConfiguration> configurations) {
        List<PluginMetrics> opened = new ArrayList<>();
        try {
            for (MetricsProviderConfiguration configuration : configurations) {
                if (configuration.isEnabled()) {
                    opened.add(openProvider(owner, configuration));
                }
            }
            if (opened.isEmpty()) {
                throw new IllegalArgumentException("At least one metrics provider must be enabled");
            }
        } catch (Throwable error) {
            closeOpened(opened);
            throw error;
        }
        return opened.size() == 1 ? opened.get(0) : new CompositePluginMetrics(opened);
    }

    private PluginMetrics openProvider(Object owner, MetricsProviderConfiguration configuration) {
        switch (configuration.getProvider()) {
            case BSTATS:
                Integer projectId = configuration.getProjectId();
                if (projectId == null) {
                    throw new IllegalArgumentException("bStats requires a project ID");
                }
                return openBStats(owner, projectId);
            case FASTSTATS:
                String token = configuration.getToken();
                if (token == null) {
                    throw new IllegalArgumentException("FastStats requires a project token");
                }
                return openFastStats(owner, token);
            default:
                throw new IllegalArgumentException("Unsupported metrics provider: " + configuration.getProvider());
        }
    }

    private static void closeOpened(List<PluginMetrics> opened) {
        for (int index = opened.size() - 1; index >= 0; index--) {
            PluginMetrics session = opened.get(index);
            Failures.runCatchingNonFatal(() -> {
                session.close();
                return null;
            });
        }
    }
}
