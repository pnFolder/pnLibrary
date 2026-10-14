package ru.privatenull.pnlibrary.bungee

import net.md_5.bungee.api.plugin.Plugin
import org.bstats.bungeecord.Metrics
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import ru.privatenull.pnlibrary.api.metrics.MetricsProvider
import ru.privatenull.pnlibrary.api.metrics.MetricsProviderConfiguration
import ru.privatenull.pnlibrary.core.metrics.CompositePluginMetrics
import ru.privatenull.pnlibrary.core.metrics.BStatsMetricsSession
import ru.privatenull.pnlibrary.spi.metrics.PlatformMetricsFactory
import ru.privatenull.pnlibrary.internal.faststats.bungee.FastStatsMetricsSession

/** Opens independently managed bStats and FastStats sessions for BungeeCord plugins. */
class BungeeMetricsFactory : PlatformMetricsFactory {

    /** Opens every enabled metrics provider configured for the BungeeCord plugin [owner]. */
    override fun open(owner: Any, configurations: Collection<MetricsProviderConfiguration>): PluginMetrics {
        require(owner is Plugin) { "Bungee metrics owner must be a Bungee Plugin" }
        val delegates = configurations.filter { it.enabled }.map<MetricsProviderConfiguration, PluginMetrics> { configuration ->
            when (configuration.provider) {
                MetricsProvider.BSTATS -> open(
                    owner,
                    requireNotNull(configuration.projectId) { "bStats requires a project ID" },
                ) as PluginMetrics
                MetricsProvider.FASTSTATS -> FastStatsMetricsSession(
                    owner,
                    requireNotNull(configuration.token) { "FastStats requires a project token" },
                ) as PluginMetrics
            }
        }
        require(delegates.isNotEmpty()) { "At least one metrics provider must be enabled" }
        return if (delegates.size == 1) delegates.single() else CompositePluginMetrics(delegates)
    }

    /**
     * Opens a BungeeCord bStats session.
     *
     * @throws IllegalArgumentException when [owner] is not a BungeeCord [Plugin]
     */
    override fun open(owner: Any, projectId: Int): PluginMetrics {
        require(owner is Plugin) { "Bungee metrics owner must be a Bungee Plugin" }
        val metrics = Metrics(owner, projectId)
        return BStatsMetricsSession(projectId, metrics::addCustomChart, metrics::shutdown)
    }
}
