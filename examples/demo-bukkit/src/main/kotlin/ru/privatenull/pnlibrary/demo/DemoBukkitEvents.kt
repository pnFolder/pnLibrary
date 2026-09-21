package ru.privatenull.pnlibrary.demo

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class DemoBukkitEvents(private val plugin: DemoPlugin) : Listener {
    @EventHandler fun onJoin(@Suppress("UNUSED_PARAMETER") event: PlayerJoinEvent) {
        plugin.state.joins.incrementAndGet()
    }
}
