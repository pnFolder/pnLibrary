package org.pnfolder.pnlibrary.bungee

import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.connection.ProxiedPlayer
import org.pnfolder.pnlibrary.api.audiences.Delivery
import org.pnfolder.pnlibrary.api.audiences.PlayerCapability
import org.pnfolder.pnlibrary.api.audiences.PlayerEffect
import org.pnfolder.pnlibrary.api.audiences.PlayerParticle
import org.pnfolder.pnlibrary.api.proxy.NetworkPlayer
import org.pnfolder.pnlibrary.api.proxy.NetworkServer
import java.net.InetSocketAddress
import java.util.UUID

/**
 * [NetworkPlayer] backed by a BungeeCord [ProxiedPlayer].
 *
 * Components are converted to legacy Bungee chat format. Effects and particles are
 * server-side operations the proxy cannot perform and return [Delivery.Unsupported].
 */
@Suppress("DEPRECATION")
internal class BungeeNetworkPlayer(
    internal val native: ProxiedPlayer,
) : NetworkPlayer {

    override val capabilities: Set<PlayerCapability> = setOf(
        PlayerCapability.MESSAGE,
        PlayerCapability.ACTION_BAR,
    )

    override val uniqueId: UUID get() = native.uniqueId
    override val name: String get() = native.name
    override val address: InetSocketAddress? get() = native.address
    override val ping: Long get() = native.ping.toLong()
    override val currentServer: NetworkServer?
        get() = native.server?.info?.let(::BungeeNetworkServer)

    override fun hasPermission(permission: String): Boolean = native.hasPermission(permission)

    override fun sendMessage(text: Component) {
        native.sendMessage(*TextComponent.fromLegacyText(LEGACY.serialize(text)))
    }

    override fun actionBar(text: Component) {
        native.sendMessage(ChatMessageType.ACTION_BAR, *TextComponent.fromLegacyText(LEGACY.serialize(text)))
    }

    override fun playSound(sound: Sound): Delivery = Delivery.Unsupported
    override fun applyEffect(effect: PlayerEffect): Delivery = Delivery.Unsupported
    override fun spawnParticle(particle: PlayerParticle): Delivery = Delivery.Unsupported

    override fun disconnect(reason: String) = native.disconnect(TextComponent(reason))

    companion object {
        private val LEGACY = LegacyComponentSerializer.legacySection()

        fun of(player: ProxiedPlayer): NetworkPlayer = BungeeNetworkPlayer(player)
    }
}
