package org.pnfolder.pnlibrary.velocity.network

import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import com.velocitypowered.api.proxy.server.RegisteredServer
import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.core.menu.OwnerInfo
import org.pnfolder.pnlibrary.api.proxy.NetworkPlayer
import org.pnfolder.pnlibrary.api.proxy.NetworkProxy
import org.pnfolder.pnlibrary.api.proxy.NetworkServer
import java.net.InetSocketAddress
import java.util.UUID

/** [NetworkProxy] backed by a Velocity [ProxyServer]. */
internal class VelocityNetworkProxy(private val server: ProxyServer) : NetworkProxy {

    override val name: String get() = server.version.name
    override val version: String get() = server.version.version
    override val playerCount: Int get() = server.playerCount

    override val players get() = server.allPlayers.map(::VelocityNetworkPlayer)
    override val servers get() = server.allServers.map(::VelocityNetworkServer)

    override fun player(name: String): NetworkPlayer? =
        server.getPlayer(name).orElse(null)?.let(::VelocityNetworkPlayer)

    override fun player(uuid: UUID): NetworkPlayer? =
        server.getPlayer(uuid).orElse(null)?.let(::VelocityNetworkPlayer)

    override fun server(name: String): NetworkServer? =
        server.getServer(name).orElse(null)?.let(::VelocityNetworkServer)

    override fun registerChannel(channel: String) =
        server.channelRegistrar.register(MinecraftChannelIdentifier.from(channel))

    override fun unregisterChannel(channel: String) =
        server.channelRegistrar.unregister(MinecraftChannelIdentifier.from(channel))

    override fun broadcast(message: String) =
        server.allPlayers.forEach { it.sendMessage(Component.text(message)) }

    override fun stop(reason: String?) =
        if (reason != null) server.shutdown(Component.text(reason)) else server.shutdown()

    /** Resolves the pnLibrary [OwnerInfo] for a Velocity plugin instance. Not part of [NetworkProxy]. */
    fun describePlugin(instance: Any?): OwnerInfo? =
        instance
            ?.let { server.pluginManager.fromInstance(it).orElse(null)?.description }
            ?.let { OwnerInfo(it.name.orElse(it.id), it.version.orElse(null), it.authors) }
}

/** [NetworkServer] backed by a Velocity [RegisteredServer]. */
internal class VelocityNetworkServer(private val rs: RegisteredServer) : NetworkServer {

    override val name: String get() = rs.serverInfo.name
    override val address: InetSocketAddress get() = rs.serverInfo.address
    override val onlinePlayers: Int get() = rs.playersConnected.size

    override val ping: Long? get() = rs.playersConnected
        .map { it.ping }
        .takeIf { it.isNotEmpty() }
        ?.average()
        ?.toLong()

    override fun send(channel: String, bytes: ByteArray): Boolean =
        rs.sendPluginMessage(MinecraftChannelIdentifier.from(channel), bytes)

    // RegisteredServer implements Audience — broadcasts to all players on this server.
    override fun broadcast(message: String) = rs.sendMessage(Component.text(message))
}
