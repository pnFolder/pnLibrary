package ru.privatenull.pnlibrary.bungee

import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.Component
import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.connection.ProxiedPlayer
import ru.privatenull.pnlibrary.api.actions.LibraryPlayer
import ru.privatenull.pnlibrary.api.actions.PlayerEffect
import ru.privatenull.pnlibrary.api.actions.PlayerParticle
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
    override val uniqueId: UUID get() = player.uniqueId
    override val name: String get() = player.name
    override fun hasPermission(permission: String): Boolean = player.hasPermission(permission)

    override fun sendMessage(text: Component) {
        player.sendMessage(*TextComponent.fromLegacyText(LEGACY.serialize(text)))
    }

    override fun actionBar(text: Component) {
        player.sendMessage(ChatMessageType.ACTION_BAR, *TextComponent.fromLegacyText(LEGACY.serialize(text)))
    }

    override fun playSound(sound: Sound): Boolean = false
    override fun applyEffect(effect: PlayerEffect): Boolean = false
    override fun spawnParticle(particle: PlayerParticle): Boolean = false

    companion object {
        private val LEGACY = LegacyComponentSerializer.legacySection()

        /** Wraps [player] in the platform-neutral player contract. */
        @JvmStatic
        fun of(player: ProxiedPlayer): LibraryPlayer = BungeeLibraryPlayer(player)
    }
}
