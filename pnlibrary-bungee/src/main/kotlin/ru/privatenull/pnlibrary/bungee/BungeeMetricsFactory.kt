package ru.privatenull.pnlibrary.bungee

import net.md_5.bungee.api.plugin.Plugin
import org.bstats.bungeecord.Metrics
import ru.privatenull.pnlibrary.api.PlatformMetricsFactory
import ru.privatenull.pnlibrary.api.PluginMetrics
import ru.privatenull.pnlibrary.core.BStatsMetricsSession

class BungeeMetricsFactory : PlatformMetricsFactory {
    override fun open(owner: Any, projectId: Int): PluginMetrics {
        require(owner is Plugin) { "Bungee metrics owner must be a Bungee Plugin" }
        val metrics = Metrics(owner, projectId)
        return BStatsMetricsSession(projectId, metrics::addCustomChart, metrics::shutdown)
    }
}
