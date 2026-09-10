package ru.privatenull.pnlibrary.bukkit

import net.kyori.adventure.platform.bukkit.BukkitAudiences
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import ru.privatenull.pnlibrary.api.actions.Action
import java.util.UUID

/** Lifecycle-owned Adventure bridge that selects the correct Bukkit/Paper facet at runtime. */
class BukkitAudienceService(plugin: Plugin) : AutoCloseable {
    private val audiences = BukkitAudiences.create(plugin)

    fun player(player: Player): Action.LibraryPlayer =
        BukkitLibraryPlayer(player, audiences.player(player.uniqueId))

    fun player(playerId: UUID): Action.LibraryPlayer? {
        val player = pluginServer.getPlayer(playerId) ?: return null
        return player(player)
    }

    private val pluginServer = plugin.server

    override fun close() {
        audiences.close()
    }
}
