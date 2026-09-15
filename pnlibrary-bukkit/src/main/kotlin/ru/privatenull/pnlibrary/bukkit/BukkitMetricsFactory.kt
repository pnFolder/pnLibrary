package ru.privatenull.pnlibrary.bukkit

import org.bstats.bukkit.Metrics
import org.bukkit.plugin.Plugin
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import ru.privatenull.pnlibrary.core.metrics.BStatsMetricsSession
import ru.privatenull.pnlibrary.spi.metrics.PlatformMetricsFactory

/** Creates independently managed bStats sessions for Bukkit [Plugin] owners. */
class BukkitMetricsFactory : PlatformMetricsFactory {
    /**
     * Opens a Bukkit bStats session.
     *
     * @throws IllegalArgumentException when [owner] is not a Bukkit [Plugin]
     */
    override fun open(owner: Any, projectId: Int): PluginMetrics {
        require(owner is Plugin) { "Bukkit metrics owner must be a Bukkit Plugin" }
        val metrics = Metrics(owner, projectId)
        return BStatsMetricsSession(projectId, metrics::addCustomChart, metrics::shutdown)
    }
}
