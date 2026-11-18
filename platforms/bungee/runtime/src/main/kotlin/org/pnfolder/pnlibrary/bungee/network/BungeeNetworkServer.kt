package org.pnfolder.pnlibrary.bungee.network

import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.config.ServerInfo
import org.pnfolder.pnlibrary.api.proxy.NetworkPlayer
import org.pnfolder.pnlibrary.api.proxy.NetworkServer
import java.net.InetSocketAddress

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
