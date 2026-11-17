package org.pnfolder.pnlibrary.bukkit.integration

import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.bukkit.compat.ServerCapabilities
import org.pnfolder.pnlibrary.bukkit.currency.error
import org.pnfolder.pnlibrary.bukkit.currency.settings
import org.pnfolder.pnlibrary.bukkit.tasks.ServerScheduler
import org.pnfolder.pnlibrary.api.logging.PnLogger
import org.pnfolder.pnlibrary.core.metrics.bstats.BStatsClient
import org.pnfolder.pnlibrary.core.metrics.bstats.BStatsSettings
import java.io.File

/** What bStats needs to know about a Bukkit server; the sending itself is shared by every platform. */
internal object BukkitBStats {
    /** Creates the bStats client for [plugin] with the Bukkit-specific fields and thread hand-off. */
    fun open(plugin: Plugin, serviceId: Int, pnLogger: () -> PnLogger): BStatsClient = BStatsClient(
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
        mainThread = if (ServerCapabilities.isFolia) null else ServerScheduler(plugin)::nextTick,
        ownerEnabled = plugin::isEnabled,
        warn = { message, error -> pnLogger().warning(message, error) },
        info = pnLogger()::info,
    )
}
