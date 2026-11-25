package org.pnfolder.pnlibrary.bungee.network

import java.time.Duration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.Component
import net.kyori.adventure.audience.ForwardingAudience
import net.kyori.adventure.audience.Audience
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.config.ServerInfo
import org.pnfolder.pnlibrary.api.network.NetworkPlayer
import org.pnfolder.pnlibrary.api.network.NetworkServer
import java.net.InetSocketAddress

@Suppress("DEPRECATION")
internal class BungeeNetworkServer(private val server: ServerInfo) : NetworkServer, ForwardingAudience {
    override fun audiences(): Iterable<Audience> = server.players.map(BungeeNetworkPlayer::of)


    override val name: String get() = server.name
    override val address: InetSocketAddress get() = server.address
    override val onlinePlayers: Int get() = server.players.size
    override val ping: Duration? get() = server.players
        .map { it.ping.toLong() }
        .takeIf { it.isNotEmpty() }
        ?.average()
        ?.let { Duration.ofMillis(it.toLong()) }
    override val motd: Component get() = LEGACY.deserialize(server.motd)
    override val isRestricted: Boolean get() = server.isRestricted

    override fun send(channel: String, bytes: ByteArray): Boolean =
        server.sendData(channel, bytes, false)

    override fun canAccess(player: NetworkPlayer): Boolean =
        (player as? BungeeNetworkPlayer)?.native?.let { server.canAccess(it) } ?: true


    private companion object {
        val LEGACY = LegacyComponentSerializer.legacySection()
    }
}
