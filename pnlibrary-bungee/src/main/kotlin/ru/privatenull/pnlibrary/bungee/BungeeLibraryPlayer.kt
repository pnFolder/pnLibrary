package ru.privatenull.pnlibrary.bungee

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.connection.ProxiedPlayer
import ru.privatenull.pnlibrary.api.actions.Action
import java.util.UUID

/** Adventure-to-Bungee bridge isolated from the platform-independent API. */
class BungeeLibraryPlayer private constructor(private val player: ProxiedPlayer) : Action.LibraryPlayer {
    override val uniqueId: UUID get() = player.uniqueId
    override val name: String get() = player.name

    override fun sendMessage(message: Component) {
        player.sendMessage(*TextComponent.fromLegacyText(LEGACY.serialize(message)))
    }

    companion object {
        private val LEGACY = LegacyComponentSerializer.legacySection()
        @JvmStatic fun of(player: ProxiedPlayer): Action.LibraryPlayer = BungeeLibraryPlayer(player)
    }
}
