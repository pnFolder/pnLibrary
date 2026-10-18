package ru.privatenull.pnlibrary.bukkit

import dev.faststats.bukkit.BukkitContext
import org.bstats.bukkit.Metrics
import org.bukkit.plugin.Plugin
import ru.privatenull.pnlibrary.core.metrics.NativePluginMetricsFactory
import ru.privatenull.pnlibrary.core.metrics.bstats.BStatsMetricsSession
import ru.privatenull.pnlibrary.core.metrics.faststats.SimpleFastStatsMetricsSession

/** Opens independently managed bStats and FastStats sessions for Bukkit plugins. */
internal class BukkitMetricsFactory : NativePluginMetricsFactory<Plugin>(
    ownerType = Plugin::class.java,
    platformName = "Bukkit",
    bStats = { plugin, projectId ->
        val metrics = Metrics(plugin, projectId)
        BStatsMetricsSession(projectId, metrics::addCustomChart, metrics::shutdown)
    },
    fastStats = { plugin, token ->
        SimpleFastStatsMetricsSession(token) { contextToken -> BukkitContext.Factory(plugin, contextToken) }
    },
)
