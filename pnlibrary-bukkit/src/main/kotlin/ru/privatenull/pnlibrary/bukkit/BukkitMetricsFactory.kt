package ru.privatenull.pnlibrary.bukkit

import org.bstats.bukkit.Metrics
import org.bukkit.plugin.Plugin
import ru.privatenull.pnlibrary.api.metrics.PlatformMetricsFactory
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import ru.privatenull.pnlibrary.core.metrics.BStatsMetricsSession

class BukkitMetricsFactory : PlatformMetricsFactory {
    override fun open(owner: Any, projectId: Int): PluginMetrics {
        require(owner is Plugin) { "Bukkit metrics owner must be a Bukkit Plugin" }
        val metrics = Metrics(owner, projectId)
        return BStatsMetricsSession(projectId, metrics::addCustomChart, metrics::shutdown)
    }
}
