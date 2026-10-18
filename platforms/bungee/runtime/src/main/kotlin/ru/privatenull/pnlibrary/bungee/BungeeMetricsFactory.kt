package ru.privatenull.pnlibrary.bungee

import dev.faststats.bungee.BungeeContext
import net.md_5.bungee.api.plugin.Plugin
import org.bstats.bungeecord.Metrics
import ru.privatenull.pnlibrary.core.metrics.NativePluginMetricsFactory
import ru.privatenull.pnlibrary.core.metrics.bstats.BStatsMetricsSession
import ru.privatenull.pnlibrary.core.metrics.faststats.SimpleFastStatsMetricsSession

/** Opens independently managed bStats and FastStats sessions for BungeeCord plugins. */
class BungeeMetricsFactory : NativePluginMetricsFactory<Plugin>(
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
