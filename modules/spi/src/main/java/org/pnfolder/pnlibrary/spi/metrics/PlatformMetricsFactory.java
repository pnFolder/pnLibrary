package org.pnfolder.pnlibrary.spi.metrics;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics;
import org.pnfolder.pnlibrary.api.metrics.error.ErrorReporter;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsProviderConfiguration;

import java.util.Collection;

/**
 * Runtime-only bridge that creates platform-specific metrics sessions.
 *
 * <p>Each successful {@link #open} call returns an independently closeable session bound to its native
 * owner. The factory should validate credentials before allocating native resources and
 * propagate startup failures so the caller can roll back plugin registration.
 */
public interface PlatformMetricsFactory {
    /** Opens the optional provider-backed error reporter. */
    @Nullable
    default ErrorReporter openErrorReporter(
            @NotNull Object owner, @NotNull Collection<MetricsProviderConfiguration> configurations) {
        return null;
    }

    /** Opens one session covering every enabled provider in {@code configurations}. */
    @NotNull
    PluginMetrics open(@NotNull Object owner, @NotNull Collection<MetricsProviderConfiguration> configurations);
}
