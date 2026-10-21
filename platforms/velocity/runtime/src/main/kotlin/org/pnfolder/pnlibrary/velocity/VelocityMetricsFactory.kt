package org.pnfolder.pnlibrary.velocity

import com.velocitypowered.api.proxy.ProxyServer
import org.bstats.velocity.Metrics
import org.slf4j.Logger
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics
import org.pnfolder.pnlibrary.core.metrics.ProviderMetricsFactory
import org.pnfolder.pnlibrary.core.metrics.bstats.BStatsMetricsSession
import org.pnfolder.pnlibrary.velocity.faststats.FastStatsVelocityProvider
import java.nio.file.Path

/** Creates bStats and FastStats sessions for Velocity using explicit platform adapters. */
internal class VelocityMetricsFactory(
    private val factory: Metrics.Factory,
    private val server: ProxyServer,
    private val logger: Logger,
    private val dataDirectory: Path,
) : ProviderMetricsFactory() {

    override fun openBStats(owner: Any, projectId: Int): PluginMetrics {
        val metrics = factory.make(owner, projectId)
        return BStatsMetricsSession(projectId, metrics::addCustomChart, metrics::shutdown)
    }

    /** FastStats on Velocity is compiled for Java 21, so the provider is created only when it can load. */
    override fun openFastStats(owner: Any, token: String): PluginMetrics {
        check(Runtime.version().feature() >= 21) {
            "FastStats on Velocity requires Java 21 or newer"
        }
        return FastStatsVelocityProvider().open(owner, server, logger, dataDirectory, token)
    }
}
