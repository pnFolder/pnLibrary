package ru.privatenull.pnlibrary.velocity

import org.bstats.velocity.Metrics
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import ru.privatenull.pnlibrary.core.metrics.BStatsMetricsSession
import ru.privatenull.pnlibrary.spi.metrics.PlatformMetricsFactory

/** Creates independently managed Velocity bStats sessions through the injected native factory. */
class VelocityMetricsFactory(
    private val factory: Metrics.Factory,
) : PlatformMetricsFactory {
    /** Opens a Velocity bStats session for the native plugin [owner]. */
    override fun open(owner: Any, projectId: Int): PluginMetrics {
        val metrics = factory.make(owner, projectId)
        return BStatsMetricsSession(projectId, metrics::addCustomChart, metrics::shutdown)
    }
}
