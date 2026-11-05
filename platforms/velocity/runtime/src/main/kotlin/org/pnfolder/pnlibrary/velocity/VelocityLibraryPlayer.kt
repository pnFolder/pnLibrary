package org.pnfolder.pnlibrary.velocity

import com.velocitypowered.api.proxy.Player
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.audiences.Delivery
import org.pnfolder.pnlibrary.api.audiences.LibraryPlayer
import org.pnfolder.pnlibrary.api.audiences.PlayerCapability
import org.pnfolder.pnlibrary.api.audiences.PlayerEffect
import org.pnfolder.pnlibrary.api.audiences.PlayerParticle
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
    override val capabilities: Set<PlayerCapability> = setOf(
        PlayerCapability.MESSAGE,
        PlayerCapability.ACTION_BAR,
        PlayerCapability.SOUND,
    )
    override val uniqueId: UUID
        get() = player.uniqueId

    override val name: String
        get() = player.username

    override fun hasPermission(permission: String): Boolean = player.hasPermission(permission)

    override fun sendMessage(text: Component) = player.sendMessage(text)

    override fun actionBar(text: Component) = player.sendActionBar(text)

    override fun playSound(sound: Sound): Delivery {
        player.playSound(sound)
        return Delivery.Delivered
    }

    /** Velocity cannot apply server-side potion effects through its proxy API. */
    override fun applyEffect(effect: PlayerEffect): Delivery = Delivery.Unsupported

    /** Velocity cannot spawn server-side particles through its proxy API. */
    override fun spawnParticle(particle: PlayerParticle): Delivery = Delivery.Unsupported

    companion object {
        /** Wraps [player] in the platform-neutral player contract. */
        @JvmStatic
        fun of(player: Player): LibraryPlayer = VelocityLibraryPlayer(player)
    }
}
