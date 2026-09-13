package ru.privatenull.pnlibrary.bungee

import net.kyori.adventure.text.Component
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.connection.ProxiedPlayer
import ru.privatenull.pnlibrary.api.actions.Action
import ru.privatenull.pnlibrary.api.actions.PlayerEffect
import ru.privatenull.pnlibrary.api.actions.PlayerParticle
import java.util.UUID

/** Adventure-to-Bungee bridge isolated from the platform-independent API. */
class BungeeLibraryPlayer private constructor(private val player: ProxiedPlayer) : ru.privatenull.pnlibrary.api.actions.LibraryPlayer {
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
        @JvmStatic fun of(player: ProxiedPlayer): ru.privatenull.pnlibrary.api.actions.LibraryPlayer = BungeeLibraryPlayer(player)
    }
}
