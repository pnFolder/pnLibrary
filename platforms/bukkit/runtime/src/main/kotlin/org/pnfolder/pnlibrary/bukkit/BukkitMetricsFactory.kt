package org.pnfolder.pnlibrary.bukkit

import dev.faststats.bukkit.BukkitContext
import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.core.metrics.NativePluginMetricsFactory
import org.pnfolder.pnlibrary.core.metrics.bstats.BStatsMetricsSession
import org.pnfolder.pnlibrary.core.metrics.faststats.SimpleFastStatsMetricsSession

/** Opens independently managed bStats and FastStats sessions for Bukkit plugins. */
internal class BukkitMetricsFactory : NativePluginMetricsFactory<Plugin>(
    ownerType = Plugin::class.java,
    platformName = "Bukkit",
    bStats = { plugin, projectId ->
        val client = BukkitBStats.open(plugin, projectId)
        BStatsMetricsSession(projectId, client::addCustomChart, client::shutdown)
    },
    fastStats = { plugin, token ->
        SimpleFastStatsMetricsSession(token) { contextToken -> BukkitContext.Factory(plugin, contextToken) }
    },
)
