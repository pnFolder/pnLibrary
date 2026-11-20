package org.pnfolder.pnlibrary.bungee.metrics

import dev.faststats.bungee.BungeeContext
import net.md_5.bungee.api.plugin.Plugin
import org.pnfolder.pnlibrary.api.logging.PnLogger
import org.pnfolder.pnlibrary.core.metrics.bstats.BStatsClient
import org.pnfolder.pnlibrary.core.metrics.bstats.BStatsMetricsSession
import org.pnfolder.pnlibrary.core.metrics.bstats.BStatsSettings
import org.pnfolder.pnlibrary.core.metrics.factory.NativePluginMetricsFactory
import org.pnfolder.pnlibrary.core.metrics.faststats.SimpleFastStatsMetricsSession
import java.io.File

internal class BungeeMetricsFactory(pnLogger: () -> PnLogger) : NativePluginMetricsFactory<Plugin>(
    ownerType = Plugin::class.java,
    platformName = "BungeeCord",
    bStats = { plugin, projectId ->
        val client = openBStats(plugin, projectId, pnLogger)
        BStatsMetricsSession(projectId, client::addCustomChart, client::shutdown)
    },
    fastStats = { plugin, token ->
        SimpleFastStatsMetricsSession(token) { contextToken -> BungeeContext.Factory(plugin, contextToken) }
    },
)

private fun openBStats(plugin: Plugin, serviceId: Int, pnLogger: () -> PnLogger): BStatsClient = BStatsClient(
    platform = "bungeecord",
    serviceId = serviceId,
    settings = BStatsSettings.yaml(File(File(plugin.dataFolder.parentFile, "bStats"), "config.yml")),
    pluginVersion = plugin.description.version,
    appendPlatformData = { data ->
        data.appendField("playerAmount", plugin.proxy.onlineCount)
        data.appendField("managedServers", plugin.proxy.servers.size)
        data.appendField("onlineMode", if (plugin.proxy.config.isOnlineMode) 1 else 0)
        data.appendField("bungeecordVersion", plugin.proxy.version)
        data.appendField("bungeecordName", plugin.proxy.name)
    },
    warn = { message, error -> pnLogger().warning(message, error) },
    info = { message -> pnLogger().info(message) },
)
