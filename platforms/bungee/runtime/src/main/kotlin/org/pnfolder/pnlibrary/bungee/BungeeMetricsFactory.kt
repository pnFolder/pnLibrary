package org.pnfolder.pnlibrary.bungee

import dev.faststats.bungee.BungeeContext
import net.md_5.bungee.api.plugin.Plugin
import org.bstats.bungeecord.Metrics
import org.pnfolder.pnlibrary.core.metrics.NativePluginMetricsFactory
import org.pnfolder.pnlibrary.core.metrics.bstats.BStatsMetricsSession
import org.pnfolder.pnlibrary.core.metrics.faststats.SimpleFastStatsMetricsSession

/** Opens independently managed bStats and FastStats sessions for BungeeCord plugins. */
internal class BungeeMetricsFactory : NativePluginMetricsFactory<Plugin>(
    ownerType = Plugin::class.java,
    platformName = "BungeeCord",
    bStats = { plugin, projectId ->
        val metrics = Metrics(plugin, projectId)
        BStatsMetricsSession(projectId, metrics::addCustomChart, metrics::shutdown)
    },
    fastStats = { plugin, token ->
        SimpleFastStatsMetricsSession(token) { contextToken -> BungeeContext.Factory(plugin, contextToken) }
    },
)
