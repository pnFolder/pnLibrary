package ru.privatenull.pnlibrary.bungee

import net.md_5.bungee.api.plugin.Plugin
import org.bstats.bungeecord.Metrics
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import ru.privatenull.pnlibrary.api.metrics.MetricsProviderConfiguration
import ru.privatenull.pnlibrary.core.metrics.BStatsMetricsSession
import ru.privatenull.pnlibrary.core.metrics.openConfiguredMetrics
import ru.privatenull.pnlibrary.spi.metrics.PlatformMetricsFactory
import ru.privatenull.pnlibrary.bungee.metrics.FastStatsMetricsSession

/** Opens independently managed bStats and FastStats sessions for BungeeCord plugins. */
class BungeeMetricsFactory : PlatformMetricsFactory {

    /** Opens every enabled metrics provider configured for the BungeeCord plugin [owner]. */
    override fun open(owner: Any, configurations: Collection<MetricsProviderConfiguration>): PluginMetrics {
        require(owner is Plugin) { "Bungee metrics owner must be a Bungee Plugin" }
        return openConfiguredMetrics(
            configurations,
            bStats = { projectId -> open(owner, projectId) },
            fastStats = { configuration -> FastStatsMetricsSession(owner, requireNotNull(configuration.token) {
                "FastStats requires a project token"
            }) },
        )
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
