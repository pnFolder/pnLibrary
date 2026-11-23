package org.pnfolder.pnlibrary.velocity.network

import com.velocitypowered.api.proxy.Player
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import org.pnfolder.pnlibrary.api.network.NetworkPlayer
import org.pnfolder.pnlibrary.api.network.NetworkServer
import org.pnfolder.pnlibrary.api.player.Delivery
import org.pnfolder.pnlibrary.api.player.PlayerCapability
import org.pnfolder.pnlibrary.api.player.PlayerEffect
import org.pnfolder.pnlibrary.api.player.PlayerParticle
import java.net.InetSocketAddress
import java.util.UUID

internal class VelocityNetworkPlayer(
    internal val native: Player,
) : NetworkPlayer {

    override val capabilities: Set<PlayerCapability> = setOf(
        PlayerCapability.MESSAGE,
        PlayerCapability.ACTION_BAR,
        PlayerCapability.SOUND,
    )

    override val uniqueId: UUID get() = native.uniqueId
    override val name: String get() = native.username
    override val address: InetSocketAddress get() = native.remoteAddress
    override val ping: Long get() = native.ping
    override val currentServer: NetworkServer?
        get() = native.currentServer.orElse(null)?.server?.let(::VelocityNetworkServer)

    override fun hasPermission(permission: String): Boolean = native.hasPermission(permission)
    override fun sendMessage(message: Component) = native.sendMessage(message)
    override fun sendActionBar(message: Component) = native.sendActionBar(message)
    override fun playSound(sound: Sound) = native.playSound(sound)
    override fun showTitle(title: Title) = native.showTitle(title)

    override fun applyEffect(effect: PlayerEffect): Delivery = Delivery.Unsupported
    override fun spawnParticle(particle: PlayerParticle): Delivery = Delivery.Unsupported
    override fun disconnect(reason: String) = native.disconnect(Component.text(reason))

    companion object {
        fun of(player: Player): NetworkPlayer = VelocityNetworkPlayer(player)
    }
}
