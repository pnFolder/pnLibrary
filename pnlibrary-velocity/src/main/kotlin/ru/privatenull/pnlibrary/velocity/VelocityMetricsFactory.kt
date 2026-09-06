package ru.privatenull.pnlibrary.velocity

import org.bstats.velocity.Metrics
import ru.privatenull.pnlibrary.api.PlatformMetricsFactory
import ru.privatenull.pnlibrary.api.PluginMetrics
import ru.privatenull.pnlibrary.core.BStatsMetricsSession

class VelocityMetricsFactory(private val factory: Metrics.Factory) : PlatformMetricsFactory {
    override fun open(owner: Any, projectId: Int): PluginMetrics {
        val metrics = factory.make(owner, projectId)
        return BStatsMetricsSession(projectId, metrics::addCustomChart, metrics::shutdown)
    }
}
