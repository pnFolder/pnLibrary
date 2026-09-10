package ru.privatenull.pnlibrary.velocity

import com.velocitypowered.api.proxy.Player
import net.kyori.adventure.text.Component
import ru.privatenull.pnlibrary.api.actions.Action
import java.util.UUID

/** Native Adventure player bridge for Velocity. */
class VelocityLibraryPlayer private constructor(private val player: Player) : Action.LibraryPlayer {
    override val uniqueId: UUID get() = player.uniqueId
    override val name: String get() = player.username
    override fun sendMessage(message: Component) = player.sendMessage(message)

    companion object {
        @JvmStatic fun of(player: Player): Action.LibraryPlayer = VelocityLibraryPlayer(player)
    }
}
