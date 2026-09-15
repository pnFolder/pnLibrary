package ru.privatenull.pnlibrary.velocity

import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import com.velocitypowered.api.proxy.Player
import ru.privatenull.pnlibrary.api.actions.LibraryPlayer
import ru.privatenull.pnlibrary.api.actions.PlayerEffect
import ru.privatenull.pnlibrary.api.actions.PlayerParticle
import java.util.UUID

/**
 * [LibraryPlayer] bridge backed by a Velocity [Player].
 *
 * Velocity accepts Adventure components and sounds directly. Potion effects and particles are
 * server-side operations unavailable to the proxy and therefore return `false`.
 */
internal class VelocityLibraryPlayer private constructor(
    private val player: Player,
) : LibraryPlayer {
    override val uniqueId: UUID get() = player.uniqueId
    override val name: String get() = player.username
    override fun hasPermission(permission: String): Boolean = player.hasPermission(permission)
    override fun sendMessage(text: Component) = player.sendMessage(text)
    override fun actionBar(text: Component) = player.sendActionBar(text)
    override fun playSound(sound: Sound): Boolean {
        player.playSound(sound)
        return true
    }
    override fun applyEffect(effect: PlayerEffect): Boolean = false
    override fun spawnParticle(particle: PlayerParticle): Boolean = false

    companion object {
        /** Wraps [player] in the platform-neutral player contract. */
        @JvmStatic
        fun of(player: Player): LibraryPlayer = VelocityLibraryPlayer(player)
    }
}
