package ru.privatenull.pnlibrary.bukkit

import org.bstats.bukkit.Metrics
import org.bukkit.plugin.Plugin
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import ru.privatenull.pnlibrary.api.metrics.MetricsProvider
import ru.privatenull.pnlibrary.api.metrics.MetricsProviderConfiguration
import ru.privatenull.pnlibrary.core.metrics.CompositePluginMetrics
import ru.privatenull.pnlibrary.core.metrics.BStatsMetricsSession
import ru.privatenull.pnlibrary.spi.metrics.PlatformMetricsFactory

/** Creates independently managed bStats sessions for Bukkit [Plugin] owners. */
class BukkitMetricsFactory : PlatformMetricsFactory {
    /** Opens every enabled metrics provider configured for the Bukkit plugin [owner]. */
    override fun open(owner: Any, configurations: Collection<MetricsProviderConfiguration>): PluginMetrics {
        require(owner is Plugin) { "Bukkit metrics owner must be a Bukkit Plugin" }
        val delegates = configurations.filter { it.enabled }.map { configuration ->
            when (configuration.provider) {
                MetricsProvider.BSTATS -> {
                    val id = requireNotNull(configuration.projectId) { "bStats requires projectId" }
                    open(owner, id)
                }
                MetricsProvider.FASTSTATS -> {
                    val token = requireNotNull(configuration.token) { "FastStats requires token" }
                    FastStatsMetricsSession(owner, token)
                }
            }
        }
        require(delegates.isNotEmpty()) { "At least one metrics provider must be enabled" }
        return if (delegates.size == 1) delegates.single() else CompositePluginMetrics(delegates)
    }
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
