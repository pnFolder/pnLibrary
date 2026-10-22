package org.pnfolder.pnlibrary.core.events

import org.pnfolder.pnlibrary.api.events.player.PlayerJoinedEvent
import org.pnfolder.pnlibrary.api.events.player.PlayerLeftEvent
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import java.util.UUID

/**
 * Turns each platform's native join and quit notifications into the portable player events.
 *
 * Platform adapters call this from their own listeners and name no event type themselves, so a native class
 * called `PlayerJoinEvent` never clashes with the library one. Publishing is best effort: a closed event
 * service during shutdown simply drops the announcement.
 */
object PlayerLifecyclePublisher {
    /** Announces that [uniqueId] joined. */
    fun joined(library: PnLibrary, uniqueId: UUID, name: String) {
        publish(library) { PlayerJoinedEvent(uniqueId, name, library.audiences.player(uniqueId)) }
    }

    /** Announces that [uniqueId] left. */
    fun left(library: PnLibrary, uniqueId: UUID, name: String) {
        publish(library) { PlayerLeftEvent(uniqueId, name, library.audiences.player(uniqueId)) }
    }

    private inline fun publish(library: PnLibrary, create: () -> org.pnfolder.pnlibrary.api.events.Event) {
        try {
            library.events.callEvent(create())
        } catch (_: IllegalStateException) {
            // The event service or audience service is already closed: the server is shutting down.
        }
    }
}
