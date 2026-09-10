package ru.privatenull.pnlibrary.bukkit

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import ru.privatenull.pnlibrary.api.actions.Action
import java.util.UUID

/** Version-safe Bukkit player bridge; works without depending on Paper Adventure methods. */
class BukkitLibraryPlayer internal constructor(
    private val player: Player,
    private val audiences: BukkitAudienceService,
) : Action.LibraryPlayer {
    override val uniqueId: UUID get() = player.uniqueId
    override val name: String get() = player.name

    override fun sendMessage(message: Component) {
        audiences.sendMessage(player, message)
    }
}
