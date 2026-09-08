package ru.privatenull.pnlibrary.velocity

import org.bstats.velocity.Metrics
import ru.privatenull.pnlibrary.spi.metrics.PlatformMetricsFactory
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import ru.privatenull.pnlibrary.core.metrics.BStatsMetricsSession

class VelocityMetricsFactory(private val factory: Metrics.Factory) : PlatformMetricsFactory {
    override fun open(owner: Any, projectId: Int): PluginMetrics {
        val metrics = factory.make(owner, projectId)
        return BStatsMetricsSession(projectId, metrics::addCustomChart, metrics::shutdown)
    }
}
