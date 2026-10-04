package ru.privatenull.pnlibrary.velocity

import org.bstats.velocity.Metrics
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import ru.privatenull.pnlibrary.api.metrics.MetricsProvider
import ru.privatenull.pnlibrary.api.metrics.MetricsProviderConfiguration
import ru.privatenull.pnlibrary.core.metrics.CompositePluginMetrics
import com.velocitypowered.api.proxy.ProxyServer
import org.slf4j.Logger
import java.nio.file.Path
import ru.privatenull.pnlibrary.internal.faststatsvelocity.FastStatsVelocityProvider
import ru.privatenull.pnlibrary.core.metrics.BStatsMetricsSession
import ru.privatenull.pnlibrary.spi.metrics.PlatformMetricsFactory

/** Creates bStats and FastStats sessions for Velocity using explicit platform adapters. */
class VelocityMetricsFactory(
    private val factory: Metrics.Factory,
    private val server: ProxyServer,
    private val logger: Logger,
    private val dataDirectory: Path,
) : PlatformMetricsFactory {

    override fun open(owner: Any, configurations: Collection<MetricsProviderConfiguration>): PluginMetrics {
        val delegates = configurations.filter { it.enabled }.map { configuration ->
            when (configuration.provider) {
                MetricsProvider.BSTATS -> open(
                    owner,
                    requireNotNull(configuration.projectId) { "bStats requires a project ID" },
                )
                MetricsProvider.FASTSTATS -> openFastStats(owner, configuration)
            }
        }
        require(delegates.isNotEmpty()) { "At least one metrics provider must be enabled" }
        return if (delegates.size == 1) delegates.single() else CompositePluginMetrics(delegates)
    }

    /** Opens a Velocity bStats session for the native plugin [owner]. */
    override fun open(owner: Any, projectId: Int): PluginMetrics {
        val metrics = factory.make(owner, projectId)
        return BStatsMetricsSession(projectId, metrics::addCustomChart, metrics::shutdown)
    }

    private fun openFastStats(owner: Any, configuration: MetricsProviderConfiguration): PluginMetrics {
        val token = requireNotNull(configuration.token) { "FastStats requires a project token" }
        return fastStatsProvider().open(owner, server, logger, dataDirectory, token)
    }

    private fun fastStatsProvider(): FastStatsVelocityProvider {
        check(Runtime.version().feature() >= 21) {
            "FastStats on Velocity requires Java 21 or newer"
        }
        return FastStatsVelocityProvider()
    }
}
