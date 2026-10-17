package ru.privatenull.pnlibrary.bungee

import net.md_5.bungee.api.plugin.Plugin
import org.bstats.bungeecord.Metrics
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import ru.privatenull.pnlibrary.bungee.metrics.FastStatsMetricsSession
import ru.privatenull.pnlibrary.core.metrics.ProviderMetricsFactory
import ru.privatenull.pnlibrary.core.metrics.bstats.BStatsMetricsSession

/** Opens independently managed bStats and FastStats sessions for BungeeCord plugins. */
class BungeeMetricsFactory : ProviderMetricsFactory() {

    /** @throws IllegalArgumentException when [owner] is not a BungeeCord [Plugin] */
    override fun openBStats(owner: Any, projectId: Int): PluginMetrics {
        val metrics = Metrics(plugin(owner), projectId)
        return BStatsMetricsSession(projectId, metrics::addCustomChart, metrics::shutdown)
    }

    /** @throws IllegalArgumentException when [owner] is not a BungeeCord [Plugin] */
    override fun openFastStats(owner: Any, token: String): PluginMetrics =
        FastStatsMetricsSession(plugin(owner), token)

    private fun plugin(owner: Any): Plugin {
        require(owner is Plugin) { "Bungee metrics owner must be a Bungee Plugin" }
        return owner
    }
}
