package org.pnfolder.pnlibrary.api.metrics;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.metrics.error.ErrorReporter;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsProvider;
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsProviderConfiguration;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/** Public pnLibrary abstraction over the platform-specific metrics providers. */
@ApiStatus.NonExtendable
public interface MetricsService {
    /** Providers available in the current platform runtime. */
    @NotNull
    default Set<MetricsProvider> getProviders() {
        return Collections.singleton(MetricsProvider.BSTATS);
    }

    /**
     * Opens and owns one metrics session for a native plugin {@code owner}.
     *
     * <p>Every enabled entry of {@code configurations} contributes one provider to the returned session.
     *
     * @throws IllegalArgumentException when no configuration is enabled or a provider lacks credentials
     */
    @NotNull
    PluginMetrics open(@NotNull Object owner, @NotNull Collection<MetricsProviderConfiguration> configurations);

    /** Opens the optional provider-backed error reporter for the same configuration. */
    @Nullable
    default ErrorReporter openErrorReporter(@NotNull Object owner, @NotNull Collection<MetricsProviderConfiguration> configurations) {
        return null;
    }
}
