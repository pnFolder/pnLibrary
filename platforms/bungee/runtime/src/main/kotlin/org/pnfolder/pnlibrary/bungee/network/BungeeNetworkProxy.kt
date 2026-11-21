package org.pnfolder.pnlibrary.bungee.network

import net.md_5.bungee.api.ProxyServer
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.plugin.Plugin
import org.pnfolder.pnlibrary.api.network.NetworkPlayer
import org.pnfolder.pnlibrary.api.network.NetworkProxy
import org.pnfolder.pnlibrary.api.network.NetworkServer
import org.pnfolder.pnlibrary.core.menu.OwnerInfo
import java.util.UUID

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

    fun describePlugin(instance: Any?): OwnerInfo? =
        (instance as? Plugin)?.description
            ?.let { OwnerInfo(it.name, it.version, listOfNotNull(it.author)) }
}
