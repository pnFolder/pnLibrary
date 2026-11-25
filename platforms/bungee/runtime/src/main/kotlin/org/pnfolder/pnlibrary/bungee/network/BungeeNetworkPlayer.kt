package org.pnfolder.pnlibrary.bungee.network

import java.time.Duration
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.connection.ProxiedPlayer
import org.pnfolder.pnlibrary.api.network.NetworkPlayer
import org.pnfolder.pnlibrary.api.network.NetworkServer
import org.pnfolder.pnlibrary.api.audience.playerPointers
import java.net.InetSocketAddress
import java.util.UUID

@Suppress("DEPRECATION")
internal class BungeeNetworkPlayer(
    internal val native: ProxiedPlayer,
) : NetworkPlayer {

    override val uniqueId: UUID get() = native.uniqueId
    override val address: InetSocketAddress? get() = native.address
    override val ping: Duration get() = Duration.ofMillis(native.ping.toLong())
    override val currentServer: NetworkServer?
        get() = native.server?.info?.let(::BungeeNetworkServer)

    override fun pointers() = playerPointers(
        name = { native.name },
        uniqueId = native.uniqueId,
        displayName = { LEGACY.deserialize(native.displayName) },
        locale = { native.locale },
        permission = native::hasPermission,
    )

    override fun sendMessage(message: Component) =
        native.sendMessage(*TextComponent.fromLegacyText(LEGACY.serialize(message)))

    override fun sendActionBar(message: Component) =
        native.sendMessage(ChatMessageType.ACTION_BAR, *TextComponent.fromLegacyText(LEGACY.serialize(message)))


    override fun disconnect(reason: Component) =
        native.disconnect(*TextComponent.fromLegacyText(LEGACY.serialize(reason)))

    companion object {
        private val LEGACY = LegacyComponentSerializer.legacySection()

        fun of(player: ProxiedPlayer): NetworkPlayer = BungeeNetworkPlayer(player)
    }
}
