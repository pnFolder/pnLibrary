package org.pnfolder.pnlibrary.bukkit

import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.bukkit.compat.ServerCapabilities
import org.pnfolder.pnlibrary.core.metrics.bstats.BStatsClient
import org.pnfolder.pnlibrary.core.metrics.bstats.BStatsSettings
import java.io.File
import java.util.logging.Level

/** What bStats needs to know about a Bukkit server; the sending itself is shared by every platform. */
internal object BukkitBStats {
    fun open(plugin: Plugin, serviceId: Int): BStatsClient = BStatsClient(
        platform = "bukkit",
        serviceId = serviceId,
        settings = BStatsSettings.yaml(File(File(plugin.dataFolder.parentFile, "bStats"), "config.yml")),
        pluginVersion = plugin.description.version,
        appendPlatformData = { data ->
            data.appendField("playerAmount", Bukkit.getOnlinePlayers().size)
            data.appendField("onlineMode", if (Bukkit.getOnlineMode()) 1 else 0)
            data.appendField("bukkitVersion", Bukkit.getVersion())
            data.appendField("bukkitName", Bukkit.getName())
        },
        // Folia has no single main thread to hand the task to, see https://github.com/Bastian/bstats-metrics/pull/126
        mainThread = if (ServerCapabilities.isFolia) null else { task -> Bukkit.getScheduler().runTask(plugin, task) },
        ownerEnabled = plugin::isEnabled,
        warn = { message, error -> plugin.logger.log(Level.WARNING, message, error) },
        info = { message -> plugin.logger.log(Level.INFO, message) },
    )

}
