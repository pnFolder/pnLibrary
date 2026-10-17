package ru.privatenull.pnlibrary.bukkit

import org.bstats.bukkit.Metrics
import org.bukkit.plugin.Plugin
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import ru.privatenull.pnlibrary.bukkit.metrics.FastStatsMetricsSession
import ru.privatenull.pnlibrary.core.metrics.ProviderMetricsFactory
import ru.privatenull.pnlibrary.core.metrics.bstats.BStatsMetricsSession

/** Opens independently managed bStats and FastStats sessions for Bukkit plugins. */
class BukkitMetricsFactory : ProviderMetricsFactory() {

    /** @throws IllegalArgumentException when [owner] is not a Bukkit [Plugin] */
    override fun openBStats(owner: Any, projectId: Int): PluginMetrics {
        val metrics = Metrics(plugin(owner), projectId)
        return BStatsMetricsSession(projectId, metrics::addCustomChart, metrics::shutdown)
    }

    /** @throws IllegalArgumentException when [owner] is not a Bukkit [Plugin] */
    override fun openFastStats(owner: Any, token: String): PluginMetrics =
        FastStatsMetricsSession(plugin(owner), token)

    private fun plugin(owner: Any): Plugin {
        require(owner is Plugin) { "Bukkit metrics owner must be a Bukkit Plugin" }
        return owner
    }
}
