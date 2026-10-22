package org.pnfolder.pnlibrary.bungee

import dev.faststats.bungee.BungeeContext
import net.md_5.bungee.api.plugin.Plugin
import org.pnfolder.pnlibrary.core.metrics.NativePluginMetricsFactory
import org.pnfolder.pnlibrary.core.metrics.bstats.BStatsMetricsSession
import org.pnfolder.pnlibrary.core.metrics.faststats.SimpleFastStatsMetricsSession

/** Opens independently managed bStats and FastStats sessions for BungeeCord plugins. */
internal class BungeeMetricsFactory : NativePluginMetricsFactory<Plugin>(
    ownerType = Plugin::class.java,
    platformName = "BungeeCord",
    bStats = { plugin, projectId ->
        val client = BungeeBStats.open(plugin, projectId)
        BStatsMetricsSession(projectId, client::addCustomChart, client::shutdown)
    },
    fastStats = { plugin, token ->
        SimpleFastStatsMetricsSession(token) { contextToken -> BungeeContext.Factory(plugin, contextToken) }
    },
)
