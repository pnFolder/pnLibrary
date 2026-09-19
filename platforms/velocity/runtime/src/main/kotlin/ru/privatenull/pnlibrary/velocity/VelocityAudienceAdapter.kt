package ru.privatenull.pnlibrary.velocity

import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.proxy.ConsoleCommandSource
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import ru.privatenull.pnlibrary.api.audiences.AudienceSender
import ru.privatenull.pnlibrary.api.actions.LibraryPlayer
import ru.privatenull.pnlibrary.spi.audiences.PlatformAudienceAdapter
import java.util.UUID

internal class VelocityAudienceAdapter(private val server: ProxyServer) : PlatformAudienceAdapter {
    override fun console(): AudienceSender = wrap(server.consoleCommandSource)
    override fun player(uniqueId: UUID): LibraryPlayer? = server.getPlayer(uniqueId).orElse(null)?.let(VelocityLibraryPlayer::of)
    override fun sender(native: Any): AudienceSender? = when (native) {
        is Player -> VelocityLibraryPlayer.of(native)
        is CommandSource -> wrap(native)
        else -> null
    }
    override fun onlinePlayers(): List<LibraryPlayer> = server.allPlayers.map(VelocityLibraryPlayer::of)

    private fun wrap(source: CommandSource) = object : AudienceSender {
        override val id = source.toString()
        override val name = source.toString()
        override val isConsole get() = source is ConsoleCommandSource
        override fun hasPermission(permission: String) = source.hasPermission(permission)
        override fun sendMessage(text: Component) = source.sendMessage(text)
        override fun actionBar(text: Component) = source.sendActionBar(text)
        override fun playSound(sound: Sound): Boolean { source.playSound(sound); return true }
    }
}
