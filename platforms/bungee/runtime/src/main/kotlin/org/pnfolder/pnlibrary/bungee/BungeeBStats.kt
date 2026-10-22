package org.pnfolder.pnlibrary.bungee

import net.md_5.bungee.api.plugin.Plugin
import org.pnfolder.pnlibrary.core.metrics.bstats.BStatsClient
import org.pnfolder.pnlibrary.core.metrics.bstats.BStatsSettings
import java.io.File
import java.util.logging.Level

/** What bStats needs to know about a BungeeCord proxy; the sending itself is shared by every platform. */
internal object BungeeBStats {
    /** Opens the sender for [plugin], which is reported under the bStats [serviceId]. */
    fun open(plugin: Plugin, serviceId: Int): BStatsClient = BStatsClient(
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
}
