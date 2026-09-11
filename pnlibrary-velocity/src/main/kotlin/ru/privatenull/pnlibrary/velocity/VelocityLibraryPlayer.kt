package ru.privatenull.pnlibrary.velocity

import com.velocitypowered.api.proxy.Player
import net.kyori.adventure.text.Component
import net.kyori.adventure.sound.Sound
import ru.privatenull.pnlibrary.api.actions.Action
import java.util.UUID

/** Native Adventure player bridge for Velocity. */
class VelocityLibraryPlayer private constructor(private val player: Player) : Action.LibraryPlayer {
    override val uniqueId: UUID get() = player.uniqueId
    override val name: String get() = player.username
    override fun sendMessage(text: Component) = player.sendMessage(text)
    override fun actionBar(text: Component) = player.sendActionBar(text)
    override fun playSound(sound: Sound): Boolean {
        player.playSound(sound)
        return true
    }

    companion object {
        @JvmStatic fun of(player: Player): Action.LibraryPlayer = VelocityLibraryPlayer(player)
    }
}
