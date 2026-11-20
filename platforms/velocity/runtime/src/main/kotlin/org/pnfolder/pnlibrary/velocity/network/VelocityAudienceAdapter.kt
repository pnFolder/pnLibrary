package org.pnfolder.pnlibrary.velocity.network

import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.proxy.ConsoleCommandSource
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.audiences.AudienceSender
import org.pnfolder.pnlibrary.api.audiences.Delivery
import org.pnfolder.pnlibrary.api.network.NetworkPlayer
import org.pnfolder.pnlibrary.spi.audiences.PlatformAudienceAdapter
import java.util.UUID

internal class VelocityAudienceAdapter(
    private val server: ProxyServer,
) : PlatformAudienceAdapter {

    override fun console(): AudienceSender = wrap(server.consoleCommandSource)

    override fun player(uniqueId: UUID): NetworkPlayer? =
        server.getPlayer(uniqueId).orElse(null)?.let(VelocityNetworkPlayer::of)

    override fun sender(native: Any): AudienceSender? = when (native) {
        is Player -> VelocityNetworkPlayer.of(native)
        is CommandSource -> wrap(native)
        else -> null
    }

    override fun onlinePlayers(): List<NetworkPlayer> =
        server.allPlayers.map(VelocityNetworkPlayer::of)

    private fun wrap(source: CommandSource): AudienceSender = object : AudienceSender {
        override val id: String = source.toString()
        override val name: String = source.toString()
        override val isConsole: Boolean
            get() = source is ConsoleCommandSource

        override fun hasPermission(permission: String): Boolean = source.hasPermission(permission)

        override fun sendMessage(text: Component) {
            source.sendMessage(text)
        }

        override fun actionBar(text: Component) {
            source.sendActionBar(text)
        }

        override fun playSound(sound: Sound): Delivery {
            source.playSound(sound)
            return Delivery.Delivered
        }
    }
}
