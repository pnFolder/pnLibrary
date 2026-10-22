package org.pnfolder.pnlibrary.bungee

import net.md_5.bungee.api.event.PlayerDisconnectEvent
import net.md_5.bungee.api.event.PostLoginEvent
import net.md_5.bungee.api.plugin.Listener
import net.md_5.bungee.event.EventHandler
import net.md_5.bungee.event.EventPriority
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.core.events.PlayerLifecyclePublisher

/** Publishes the portable player join and quit events from BungeeCord's native ones. */
internal class BungeePlayerEvents(private val library: PnLibrary) : Listener {
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPostLogin(event: PostLoginEvent) {
        PlayerLifecyclePublisher.joined(library, event.player.uniqueId, event.player.name)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onDisconnect(event: PlayerDisconnectEvent) {
        PlayerLifecyclePublisher.left(library, event.player.uniqueId, event.player.name)
    }
}
