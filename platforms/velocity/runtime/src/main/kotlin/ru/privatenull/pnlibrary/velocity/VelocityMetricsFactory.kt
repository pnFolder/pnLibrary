package ru.privatenull.pnlibrary.velocity

import org.bstats.velocity.Metrics
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import ru.privatenull.pnlibrary.api.metrics.MetricsProvider
import ru.privatenull.pnlibrary.api.metrics.MetricsProviderConfiguration
import ru.privatenull.pnlibrary.core.metrics.CompositePluginMetrics
import ru.privatenull.pnlibrary.api.metrics.ErrorReporter
import com.velocitypowered.api.proxy.ProxyServer
import org.slf4j.Logger
import java.nio.file.Path
import java.util.ServiceLoader
import ru.privatenull.pnlibrary.spi.metrics.VelocityMetricsProvider
import ru.privatenull.pnlibrary.core.metrics.BStatsMetricsSession
import ru.privatenull.pnlibrary.spi.metrics.PlatformMetricsFactory

/** Creates independently managed Velocity bStats sessions through the injected native factory. */
class VelocityMetricsFactory(
    private val factory: Metrics.Factory,
    private val plugin: Any,
    private val server: ProxyServer,
    private val logger: Logger,
    private val dataDirectory: Path,
) : PlatformMetricsFactory {
    private val optionalProvider: VelocityMetricsProvider? by lazy {
        runCatching { ServiceLoader.load(VelocityMetricsProvider::class.java).firstOrNull() }.getOrNull()
    }
    override fun openErrorReporter(owner: Any, configurations: Collection<MetricsProviderConfiguration>): ErrorReporter? =
        if (configurations.any { it.enabled && it.provider == MetricsProvider.FASTSTATS && it.token != null }) optionalProvider?.openErrorReporter() else null

    override fun open(owner: Any, configurations: Collection<MetricsProviderConfiguration>): PluginMetrics {
        val delegates = configurations.filter { it.enabled }.map {
            when (it.provider) {
                MetricsProvider.BSTATS -> open(owner, requireNotNull(it.projectId))
                MetricsProvider.FASTSTATS -> requireNotNull(optionalProvider) { "FastStats Velocity provider module is unavailable on this Java runtime" }
                    .open(owner, server, logger, dataDirectory, requireNotNull(it.token))
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
}
