package org.pnfolder.pnlibrary.bungee

import dev.faststats.bungee.BungeeContext
import net.md_5.bungee.api.plugin.Plugin
import org.pnfolder.pnlibrary.core.metrics.NativePluginMetricsFactory
import org.pnfolder.pnlibrary.core.metrics.bstats.BStatsClient
import org.pnfolder.pnlibrary.core.metrics.bstats.BStatsMetricsSession
import org.pnfolder.pnlibrary.core.metrics.bstats.BStatsSettings
import org.pnfolder.pnlibrary.core.metrics.faststats.SimpleFastStatsMetricsSession
import java.io.File
import java.util.logging.Level

/** Opens independently managed bStats and FastStats sessions for BungeeCord plugins. */
internal class BungeeMetricsFactory : NativePluginMetricsFactory<Plugin>(
    ownerType = Plugin::class.java,
    platformName = "BungeeCord",
    bStats = { plugin, projectId ->
        val client = openBStats(plugin, projectId)
        BStatsMetricsSession(projectId, client::addCustomChart, client::shutdown)
    },
    fastStats = { plugin, token ->
        SimpleFastStatsMetricsSession(token) { contextToken -> BungeeContext.Factory(plugin, contextToken) }
    },
)

/** Opens the bStats sender for [plugin], which is reported under the bStats [serviceId]; the sending itself is shared by every platform. */
private fun openBStats(plugin: Plugin, serviceId: Int): BStatsClient = BStatsClient(
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
    warn = { message, error -> plugin.logger.log(Level.WARNING, message, error) },
    info = { message -> plugin.logger.log(Level.INFO, message) },
)
