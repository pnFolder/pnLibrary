package org.pnfolder.pnlibrary.core.metrics

import org.pnfolder.pnlibrary.api.metrics.MetricsProvider
import org.pnfolder.pnlibrary.api.metrics.MetricsProviderConfiguration
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics
import org.pnfolder.pnlibrary.spi.metrics.PlatformMetricsFactory

/**
 * Base class for platform metrics factories.
 *
 * Platforms only describe how to open one native bStats or FastStats session; provider selection,
 * composition of several enabled providers and rollback on partial failure live here.
 */
abstract class ProviderMetricsFactory : PlatformMetricsFactory {

    /** Opens a bStats session for the native plugin [owner]. */
    protected abstract fun openBStats(owner: Any, projectId: Int): PluginMetrics

    /** Opens a FastStats session for the native plugin [owner]. */
    protected abstract fun openFastStats(owner: Any, token: String): PluginMetrics

    final override fun open(owner: Any, configurations: Collection<MetricsProviderConfiguration>): PluginMetrics {
        val opened = mutableListOf<PluginMetrics>()
        try {
            configurations.filter { it.enabled }.forEach { configuration -> opened += open(owner, configuration) }
            require(opened.isNotEmpty()) { "At least one metrics provider must be enabled" }
        } catch (error: Throwable) {
            opened.asReversed().forEach { session -> runCatching { session.close() } }
            throw error
        }
        return if (opened.size == 1) opened.single() else CompositePluginMetrics(opened)
    }

    private fun open(owner: Any, configuration: MetricsProviderConfiguration): PluginMetrics =
        when (configuration.provider) {
            MetricsProvider.BSTATS -> openBStats(owner, requireNotNull(configuration.projectId) {
                "bStats requires a project ID"
            })
            MetricsProvider.FASTSTATS -> openFastStats(owner, requireNotNull(configuration.token) {
                "FastStats requires a project token"
            })
        }
}
