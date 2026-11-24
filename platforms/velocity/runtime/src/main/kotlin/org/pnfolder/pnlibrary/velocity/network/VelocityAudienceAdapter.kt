package org.pnfolder.pnlibrary.velocity.network

import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.proxy.ConsoleCommandSource
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.audience.ForwardingAudience
import org.pnfolder.pnlibrary.api.network.NetworkPlayer
import org.pnfolder.pnlibrary.api.audience.senderPointers
import org.pnfolder.pnlibrary.spi.audience.PlatformAudienceAdapter
import java.util.UUID

internal class VelocityAudienceAdapter(
    private val server: ProxyServer,
) : PlatformAudienceAdapter {

    override fun console(): Audience = wrap(server.consoleCommandSource)

    override fun player(uniqueId: UUID): NetworkPlayer? =
        server.getPlayer(uniqueId).orElse(null)?.let(VelocityNetworkPlayer::of)

    override fun of(native: Any): Audience? = when (native) {
        is Player -> VelocityNetworkPlayer.of(native)
        is CommandSource -> wrap(native)
        else -> null
    }

    override fun players(): List<NetworkPlayer> =
        server.allPlayers.map(VelocityNetworkPlayer::of)

    /** The source is already an Adventure audience; this only gives the console a name and a permission check. */
    private fun wrap(source: CommandSource): Audience = object : ForwardingAudience.Single {
        override fun audience(): Audience = source

        override fun pointers() = senderPointers(
            if (source is ConsoleCommandSource) "Console" else source.toString(),
            permission = source::hasPermission,
        )
    }
}
