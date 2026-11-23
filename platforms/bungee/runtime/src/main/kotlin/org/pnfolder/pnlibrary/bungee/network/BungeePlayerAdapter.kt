package org.pnfolder.pnlibrary.bungee.network

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.md_5.bungee.api.CommandSender
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.connection.ProxiedPlayer
import net.md_5.bungee.api.plugin.Plugin
import org.pnfolder.pnlibrary.api.network.NetworkPlayer
import org.pnfolder.pnlibrary.api.sender.senderPointers
import org.pnfolder.pnlibrary.spi.player.PlatformPlayerAdapter
import java.util.UUID

@Suppress("DEPRECATION")
internal class BungeePlayerAdapter(
    private val plugin: Plugin,
) : PlatformPlayerAdapter {

    override fun console(): Audience = wrap(plugin.proxy.console)

    override fun player(uniqueId: UUID): NetworkPlayer? =
        plugin.proxy.getPlayer(uniqueId)?.let(BungeeNetworkPlayer::of)

    override fun sender(native: Any): Audience? = when (native) {
        is ProxiedPlayer -> BungeeNetworkPlayer.of(native)
        is CommandSender -> wrap(native)
        else -> null
    }

    override fun onlinePlayers(): List<NetworkPlayer> =
        plugin.proxy.players.map(BungeeNetworkPlayer::of)

    private fun wrap(sender: CommandSender): Audience = object : Audience {
        override fun pointers() = senderPointers(sender.name, permission = sender::hasPermission)

        override fun sendMessage(message: Component) = sender.sendMessage(*TextComponent.fromLegacyText(LEGACY.serialize(message)))

        override fun sendActionBar(message: Component) = sendMessage(message)
    }

    private companion object {
        private val LEGACY = LegacyComponentSerializer.legacySection()
    }
}
