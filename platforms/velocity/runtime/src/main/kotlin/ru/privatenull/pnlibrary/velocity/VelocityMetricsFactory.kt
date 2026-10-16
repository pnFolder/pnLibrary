package ru.privatenull.pnlibrary.velocity

import com.velocitypowered.api.proxy.ProxyServer
import org.bstats.velocity.Metrics
import org.slf4j.Logger
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import ru.privatenull.pnlibrary.api.metrics.MetricsProviderConfiguration
import ru.privatenull.pnlibrary.core.metrics.BStatsMetricsSession
import ru.privatenull.pnlibrary.core.metrics.openConfiguredMetrics
import ru.privatenull.pnlibrary.internal.faststatsvelocity.FastStatsVelocityProvider
import ru.privatenull.pnlibrary.spi.metrics.PlatformMetricsFactory
import java.nio.file.Path

/** Creates bStats and FastStats sessions for Velocity using explicit platform adapters. */
class VelocityMetricsFactory(
    private val factory: Metrics.Factory,
    private val server: ProxyServer,
    private val logger: Logger,
    private val dataDirectory: Path,
) : PlatformMetricsFactory {

    /** Opens every enabled metrics provider configured for the Velocity plugin [owner]. */
    override fun open(
        owner: Any,
        configurations: Collection<MetricsProviderConfiguration>,
    ): PluginMetrics {
        return openConfiguredMetrics(
            configurations,
            bStats = { projectId -> open(owner, projectId) },
            fastStats = { configuration -> openFastStats(owner, configuration) },
        )
    }

    /** Opens a Velocity bStats session for the native plugin [owner]. */
    override fun open(owner: Any, projectId: Int): PluginMetrics {
        val metrics = factory.make(owner, projectId)
        return BStatsMetricsSession(projectId, metrics::addCustomChart, metrics::shutdown)
    }

    /** Opens a FastStats session after validating the configured project token. */
    private fun openFastStats(
        owner: Any,
        configuration: MetricsProviderConfiguration,
    ): PluginMetrics {
        val token = requireNotNull(configuration.token) { "FastStats requires a project token" }
        return fastStatsProvider().open(owner, server, logger, dataDirectory, token)
    }

    /** Creates the Java-version-specific FastStats Velocity provider. */
    private fun fastStatsProvider(): FastStatsVelocityProvider {
        check(Runtime.version().feature() >= 21) {
            "FastStats on Velocity requires Java 21 or newer"
        }
        return FastStatsVelocityProvider()
    }
}
