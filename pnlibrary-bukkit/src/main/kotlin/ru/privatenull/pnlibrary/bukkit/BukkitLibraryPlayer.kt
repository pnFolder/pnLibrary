package ru.privatenull.pnlibrary.bukkit

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.entity.Player
import ru.privatenull.pnlibrary.api.actions.Action
import java.util.UUID

/** Version-safe Bukkit player bridge; works without depending on Paper Adventure methods. */
class BukkitLibraryPlayer private constructor(private val player: Player) : Action.LibraryPlayer {
    override val uniqueId: UUID get() = player.uniqueId
    override val name: String get() = player.name

    override fun sendMessage(message: Component) {
        player.sendMessage(LEGACY.serialize(message))
    }

    companion object {
        private val LEGACY = LegacyComponentSerializer.legacySection()
        @JvmStatic fun of(player: Player): Action.LibraryPlayer = BukkitLibraryPlayer(player)
    }
}
