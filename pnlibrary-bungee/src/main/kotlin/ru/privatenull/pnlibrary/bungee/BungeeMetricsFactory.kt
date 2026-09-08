package ru.privatenull.pnlibrary.bungee

import net.md_5.bungee.api.plugin.Plugin
import org.bstats.bungeecord.Metrics
import ru.privatenull.pnlibrary.spi.metrics.PlatformMetricsFactory
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import ru.privatenull.pnlibrary.core.metrics.BStatsMetricsSession

class BungeeMetricsFactory : PlatformMetricsFactory {
    override fun open(owner: Any, projectId: Int): PluginMetrics {
        require(owner is Plugin) { "Bungee metrics owner must be a Bungee Plugin" }
        val metrics = Metrics(owner, projectId)
        return BStatsMetricsSession(projectId, metrics::addCustomChart, metrics::shutdown)
    }
}
