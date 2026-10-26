package org.pnfolder.pnlibrary.bukkit

import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import org.pnfolder.pnlibrary.core.metrics.bstats.BStatsClient
import org.pnfolder.pnlibrary.core.metrics.bstats.BStatsSettings
import java.io.File
import java.util.logging.Level

/** What bStats needs to know about a Bukkit server; the sending itself is shared by every platform. */
internal object BukkitBStats {
    /** Opens the sender for [plugin], which is reported under the bStats [serviceId]. */
    fun open(plugin: Plugin, serviceId: Int): BStatsClient = BStatsClient(
        platform = "bukkit",
        serviceId = serviceId,
        settings = BStatsSettings.yaml(File(File(plugin.dataFolder.parentFile, "bStats"), "config.yml")),
        pluginVersion = plugin.description.version,
        appendPlatformData = { data ->
            data.appendField("playerAmount", playerAmount())
            data.appendField("onlineMode", if (Bukkit.getOnlineMode()) 1 else 0)
            data.appendField("bukkitVersion", Bukkit.getVersion())
            data.appendField("bukkitName", Bukkit.getName())
        },
        // Folia has no single main thread to hand the task to, see https://github.com/Bastian/bstats-metrics/pull/126
        mainThread = if (isFolia()) null else { task -> Bukkit.getScheduler().runTask(plugin, task) },
        ownerEnabled = plugin::isEnabled,
        warn = { message, error -> plugin.logger.log(Level.WARNING, message, error) },
        info = { message -> plugin.logger.log(Level.INFO, message) },
    )

    private fun isFolia(): Boolean =
        runCatchingNonFatal { Class.forName("io.papermc.paper.threadedregions.RegionizedServer") }.isSuccess

    /** Around Minecraft 1.8 `getOnlinePlayers` changed from an array to a collection; both are read. */
    private fun playerAmount(): Int = try {
        val method = Class.forName("org.bukkit.Server").getMethod("getOnlinePlayers")
        val players = method.invoke(Bukkit.getServer())
        if (method.returnType == Collection::class.java) (players as Collection<*>).size else (players as Array<*>).size
    } catch (_: Exception) {
        Bukkit.getOnlinePlayers().size
    }
}
