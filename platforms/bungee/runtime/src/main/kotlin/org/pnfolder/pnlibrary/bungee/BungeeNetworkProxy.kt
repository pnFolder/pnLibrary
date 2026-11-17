package org.pnfolder.pnlibrary.bungee

import net.md_5.bungee.api.ProxyServer
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.plugin.Plugin
import net.md_5.bungee.api.config.ServerInfo
import org.pnfolder.pnlibrary.core.menu.OwnerInfo
import org.pnfolder.pnlibrary.api.proxy.NetworkPlayer
import org.pnfolder.pnlibrary.api.proxy.NetworkProxy
import org.pnfolder.pnlibrary.api.proxy.NetworkServer
import java.net.InetSocketAddress
import java.util.UUID

/** [NetworkProxy] backed by a BungeeCord [ProxyServer]. */
@Suppress("DEPRECATION")
internal class BungeeNetworkProxy(private val proxy: ProxyServer) : NetworkProxy {

    override val name: String get() = proxy.name
    override val version: String get() = proxy.version
    override val playerCount: Int get() = proxy.onlineCount

    override val players get() = proxy.players.map(::BungeeNetworkPlayer)
    override val servers get() = proxy.servers.values.map(::BungeeNetworkServer)

    override fun player(name: String): NetworkPlayer? =
        proxy.getPlayer(name)?.let(::BungeeNetworkPlayer)

    override fun player(uuid: UUID): NetworkPlayer? =
        proxy.getPlayer(uuid)?.let(::BungeeNetworkPlayer)

    override fun server(name: String): NetworkServer? =
        proxy.servers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }
            ?.value?.let(::BungeeNetworkServer)

    override fun registerChannel(channel: String) = proxy.registerChannel(channel)
    override fun unregisterChannel(channel: String) = proxy.unregisterChannel(channel)

    override fun broadcast(message: String) = proxy.broadcast(TextComponent(message))
    override fun stop(reason: String?) =
        if (reason != null) proxy.stop(reason) else proxy.stop()

    /** Resolves the pnLibrary [OwnerInfo] for a Bungee plugin instance. Not part of [NetworkProxy]. */
    fun describePlugin(instance: Any?): OwnerInfo? =
        (instance as? Plugin)?.description
            ?.let { OwnerInfo(it.name, it.version, listOfNotNull(it.author)) }
}

/** [NetworkServer] backed by a BungeeCord [ServerInfo]. */
@Suppress("DEPRECATION")
internal class BungeeNetworkServer(private val server: ServerInfo) : NetworkServer {

    override val name: String get() = server.name
    override val address: InetSocketAddress get() = server.address
    override val onlinePlayers: Int get() = server.players.size
    override val ping: Long? get() = server.players
        .map { it.ping.toLong() }
        .takeIf { it.isNotEmpty() }
        ?.average()
        ?.toLong()
    override val motd: String get() = server.motd
    override val isRestricted: Boolean get() = server.isRestricted

    override fun send(channel: String, bytes: ByteArray): Boolean =
        server.sendData(channel, bytes, false)

    override fun canAccess(player: NetworkPlayer): Boolean =
        (player as? BungeeNetworkPlayer)?.native?.let { server.canAccess(it) } ?: true

    override fun broadcast(message: String) =
        server.players.forEach { it.sendMessage(TextComponent(message)) }
}
