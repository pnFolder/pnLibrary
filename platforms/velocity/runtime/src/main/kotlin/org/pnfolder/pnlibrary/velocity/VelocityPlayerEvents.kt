package org.pnfolder.pnlibrary.velocity

import com.velocitypowered.api.event.PostOrder
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.PostLoginEvent
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.core.events.PlayerLifecyclePublisher

/** Publishes the portable player join and quit events from Velocity's native ones. */
internal class VelocityPlayerEvents(private val library: PnLibrary) {
    @Subscribe(order = PostOrder.LAST)
    fun onPostLogin(event: PostLoginEvent) {
        PlayerLifecyclePublisher.joined(library, event.player.uniqueId, event.player.username)
    }

    @Subscribe(order = PostOrder.LAST)
    fun onDisconnect(event: DisconnectEvent) {
        PlayerLifecyclePublisher.left(library, event.player.uniqueId, event.player.username)
    }
}
