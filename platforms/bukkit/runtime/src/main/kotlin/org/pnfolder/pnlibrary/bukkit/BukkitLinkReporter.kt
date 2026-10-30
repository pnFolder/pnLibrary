package org.pnfolder.pnlibrary.bukkit

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import org.pnfolder.pnlibrary.core.link.LinkCodec
import org.pnfolder.pnlibrary.core.link.LinkReports
import org.pnfolder.pnlibrary.core.link.LinkSecret

/**
 * Sends this server's report to the proxy over the link channel, every half minute while a player is online.
 *
 * A plugin message needs a player connection to travel on, so a server with nobody online stays silent and the
 * proxy marks it as out of touch. Without the shared `link.key` the reporter does nothing.
 */
internal class BukkitLinkReporter(private val plugin: JavaPlugin, private val library: PnLibrary) {
    private var task: BukkitTask? = null

    fun start() {
        val key = LinkSecret.read(plugin.dataFolder.toPath()) ?: return
        plugin.server.messenger.registerOutgoingPluginChannel(plugin, LinkCodec.CHANNEL)
        task = plugin.server.scheduler.runTaskTimer(plugin, Runnable { report(key) }, FIRST_DELAY_TICKS, PERIOD_TICKS)
    }

    fun stop() {
        task?.cancel()
        task = null
        runCatchingNonFatal { plugin.server.messenger.unregisterOutgoingPluginChannel(plugin, LinkCodec.CHANNEL) }
    }

    private fun report(key: ByteArray) {
        if (library.isClosed) return
        val players = Bukkit.getOnlinePlayers()
        val carrier = players.firstOrNull() ?: return
        val platform = "${Bukkit.getName()} ${Bukkit.getBukkitVersion()}"
        runCatchingNonFatal {
            carrier.sendPluginMessage(plugin, LinkCodec.CHANNEL, LinkCodec.encode(LinkReports.build(library, platform, players.size), key))
        }
    }

    private companion object {
        const val FIRST_DELAY_TICKS = 100L
        const val PERIOD_TICKS = 600L
    }
}
