package org.pnfolder.pnlibrary.bukkit

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.plugin.messaging.PluginMessageListener
import org.bukkit.scheduler.BukkitTask
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.bukkit.commands.RemoteMenuClient
import org.pnfolder.pnlibrary.bukkit.commands.RemoteMenuHost
import org.pnfolder.pnlibrary.bukkit.commands.RemoteMenus
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import org.pnfolder.pnlibrary.core.link.LinkCodec
import org.pnfolder.pnlibrary.core.link.LinkHub
import org.pnfolder.pnlibrary.core.link.LinkReports
import org.pnfolder.pnlibrary.core.link.LinkSecret
import org.pnfolder.pnlibrary.core.link.RemoteExecutor
import java.time.Instant

/**
 * Sends this server's report to the proxy over the link channel, every half minute while a player is online.
 *
 * A plugin message needs a player connection to travel on, so a server with nobody online stays silent and the
 * proxy marks it as out of touch. Without the shared `link.key` the reporter does nothing.
 */
internal class BukkitLinkReporter(private val plugin: JavaPlugin, private val library: PnLibrary) : PluginMessageListener {
    private var task: BukkitTask? = null
    private var key: ByteArray? = null
    private val executor = RemoteExecutor(library)

    fun start() {
        val key = LinkSecret.read(plugin.dataFolder.toPath()) ?: return
        this.key = key
        LinkHub.key = key
        LinkHub.uplink = { bytes ->
            val carrier = Bukkit.getOnlinePlayers().firstOrNull()
            carrier?.sendPluginMessage(plugin, LinkCodec.CHANNEL, bytes)
            carrier != null
        }
        RemoteMenus.host = RemoteMenuHost(plugin, library, key)
        RemoteMenus.client = RemoteMenuClient(plugin, library, key)
        plugin.server.messenger.registerIncomingPluginChannel(plugin, LinkCodec.CHANNEL, this)
        plugin.server.messenger.registerOutgoingPluginChannel(plugin, LinkCodec.CHANNEL)
        task = plugin.server.scheduler.runTaskTimer(plugin, Runnable { report(key) }, FIRST_DELAY_TICKS, PERIOD_TICKS)
    }

    fun stop() {
        RemoteMenus.host?.stop()
        RemoteMenus.client?.stop()
        RemoteMenus.host = null
        RemoteMenus.client = null
        LinkHub.uplink = null
        LinkHub.key = null
        task?.cancel()
        task = null
        runCatchingNonFatal { plugin.server.messenger.unregisterIncomingPluginChannel(plugin, LinkCodec.CHANNEL) }
        runCatchingNonFatal { plugin.server.messenger.unregisterOutgoingPluginChannel(plugin, LinkCodec.CHANNEL) }
    }

    /** Keeps the network map that the proxy sent; a map without the right signature is dropped. */
    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {
        if (channel != LinkCodec.CHANNEL) return
        val key = key ?: return
        LinkCodec.decodeNetwork(message, key)?.let {
            LinkHub.network = LinkHub.ReceivedNetwork(it, Instant.now())
            return
        }
        LinkCodec.decodeRequest(message, key)?.let { request ->
            executor.run(request) { result ->
                plugin.server.scheduler.runTask(plugin, Runnable { player.sendPluginMessage(plugin, LinkCodec.CHANNEL, LinkCodec.encodeResult(result, key)) })
            }
            return
        }
        LinkCodec.decodeView(message, key)?.let { request ->
            RemoteMenus.host?.handle(request)
            return
        }
        LinkCodec.decodeFrame(message, key)?.let { frame ->
            RemoteMenus.client?.accept(frame)
            return
        }
        LinkCodec.decodeResult(message, key)?.let { LinkHub.replies.complete(it) }
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
