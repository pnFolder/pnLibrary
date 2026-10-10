package ru.privatenull.pnlibrary.velocity

import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.proxy.ConsoleCommandSource
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import ru.privatenull.pnlibrary.api.actions.LibraryPlayer
import ru.privatenull.pnlibrary.api.audiences.AudienceSender
import ru.privatenull.pnlibrary.spi.audiences.PlatformAudienceAdapter
import java.util.UUID

/** Adapts Velocity command sources and players to pnLibrary audiences. */
internal class VelocityAudienceAdapter(
    private val server: ProxyServer,
) : PlatformAudienceAdapter {

    /** Returns the proxy console as a portable audience. */
    override fun console(): AudienceSender = wrap(server.consoleCommandSource)

    /** Resolves a player by UUID, returning `null` when the player is offline. */
    override fun player(uniqueId: UUID): LibraryPlayer? =
        server.getPlayer(uniqueId).orElse(null)?.let(VelocityLibraryPlayer::of)

    /** Adapts a native Velocity command source when its type is supported. */
    override fun sender(native: Any): AudienceSender? = when (native) {
        is Player -> VelocityLibraryPlayer.of(native)
        is CommandSource -> wrap(native)
        else -> null
    }

    /** Returns all currently connected players. */
    override fun onlinePlayers(): List<LibraryPlayer> =
        server.allPlayers.map(VelocityLibraryPlayer::of)

    /** Creates a portable audience backed by [source]. */
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

        override fun playSound(sound: Sound): Boolean {
            source.playSound(sound)
            return true
        }
    }
}
