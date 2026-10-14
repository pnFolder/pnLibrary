package ru.privatenull.pnlibrary.core.metrics

import ru.privatenull.pnlibrary.api.metrics.MetricsProvider
import ru.privatenull.pnlibrary.api.metrics.MetricsProviderConfiguration
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics

/** Shared provider selection and composition for platform metrics factories. */
/** Runtime-only helper; platform modules use it during composition, consumers should not. */
@JvmSynthetic
fun openConfiguredMetrics(
    configurations: Collection<MetricsProviderConfiguration>,
    bStats: (projectId: Int) -> PluginMetrics,
    fastStats: (configuration: MetricsProviderConfiguration) -> PluginMetrics,
): PluginMetrics {
    val delegates = configurations.filter { it.enabled }.map { configuration ->
        when (configuration.provider) {
            MetricsProvider.BSTATS -> bStats(requireNotNull(configuration.projectId) {
                "bStats requires a project ID"
            })
            MetricsProvider.FASTSTATS -> fastStats(configuration)
        }
    }
    require(delegates.isNotEmpty()) { "At least one metrics provider must be enabled" }
    return if (delegates.size == 1) delegates.single() else CompositePluginMetrics(delegates)
}
