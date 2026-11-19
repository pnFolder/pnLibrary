package org.pnfolder.pnlibrary.api.metrics

import org.pnfolder.pnlibrary.api.metrics.provider.MetricsProviderConfiguration
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsProvider
import org.pnfolder.pnlibrary.api.metrics.error.ErrorReporter


/** Public pnLibrary abstraction over the platform-specific metrics providers. */
interface MetricsService {
    /** Providers available in the current platform runtime. */
    val providers: Set<MetricsProvider>
        get() = setOf(MetricsProvider.BSTATS)

    /**
     * Opens and owns one metrics session for a native plugin [owner].
     *
     * Every enabled entry of [configurations] contributes one provider to the returned session.
     *
     * @throws IllegalArgumentException when no configuration is enabled or a provider lacks credentials
     */
    fun open(owner: Any, configurations: Collection<MetricsProviderConfiguration>): PluginMetrics

    /** Opens the optional provider-backed error reporter for the same configuration. */
    fun openErrorReporter(owner: Any, configurations: Collection<MetricsProviderConfiguration>): ErrorReporter? = null
}
