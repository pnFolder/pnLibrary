package org.pnfolder.pnlibrary.bungee

import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.connection.ProxiedPlayer
import org.pnfolder.pnlibrary.api.audiences.Delivery
import org.pnfolder.pnlibrary.api.audiences.LibraryPlayer
import org.pnfolder.pnlibrary.api.audiences.PlayerCapability
import org.pnfolder.pnlibrary.api.audiences.PlayerEffect
import org.pnfolder.pnlibrary.api.audiences.PlayerParticle
import java.util.UUID

/**
 * [LibraryPlayer] bridge backed by a BungeeCord [ProxiedPlayer].
 *
 * Components are converted to legacy Bungee chat components. The proxy cannot directly render
 * server-side potion effects or particles, so those operations return `false`.
 *
 * Bungee's legacy component parser is deprecated in newer API releases but remains the only
 * binary-compatible conversion path across the module's supported proxy baseline.
 */
@Suppress("DEPRECATION")
internal class BungeeLibraryPlayer private constructor(
    private val player: ProxiedPlayer,
) : LibraryPlayer {
    override val capabilities: Set<PlayerCapability> = setOf(
        PlayerCapability.MESSAGE,
        PlayerCapability.ACTION_BAR,
    )
    override val uniqueId: UUID get() = player.uniqueId
    override val name: String get() = player.name

    override fun hasPermission(permission: String): Boolean = player.hasPermission(permission)

    override fun sendMessage(text: Component) {
        player.sendMessage(*TextComponent.fromLegacyText(LEGACY.serialize(text)))
    }

    override fun actionBar(text: Component) {
        player.sendMessage(ChatMessageType.ACTION_BAR, *TextComponent.fromLegacyText(LEGACY.serialize(text)))
    }

    /** Returns [Delivery.Unsupported] because this proxy adapter cannot play server-side sounds. */
    override fun playSound(sound: Sound): Delivery = Delivery.Unsupported

    /** Returns [Delivery.Unsupported] because potion effects require a backend server. */
    override fun applyEffect(effect: PlayerEffect): Delivery = Delivery.Unsupported

    /** Returns [Delivery.Unsupported] because particle rendering requires a backend server. */
    override fun spawnParticle(particle: PlayerParticle): Delivery = Delivery.Unsupported

    companion object {
        private val LEGACY = LegacyComponentSerializer.legacySection()

        @JvmStatic
        fun of(player: ProxiedPlayer): LibraryPlayer = BungeeLibraryPlayer(player)
    }
}
