package org.pnfolder.pnlibrary.api.events.player

import org.pnfolder.pnlibrary.api.audiences.LibraryPlayer
import org.pnfolder.pnlibrary.api.events.Event
import java.util.UUID

/**
 * Platform-independent notification about a player entering or leaving the server or proxy.
 *
 * pnLibrary publishes these through the event service on Bukkit, BungeeCord and Velocity, so one listener
 * works everywhere. They are announcements: a listener cannot veto the join or the quit.
 *
 * @property uniqueId the player's account ID
 * @property name the player's name at that moment
 * @property player the player as a library audience, or `null` when the platform no longer knows the player
 */
abstract class PlayerLifecycleEvent(
    val uniqueId: UUID,
    val name: String,
    val player: LibraryPlayer?,
) : Event()

/** A player finished joining: on Bukkit after the join event, on a proxy after login. */
class PlayerJoinedEvent(uniqueId: UUID, name: String, player: LibraryPlayer?) :
    PlayerLifecycleEvent(uniqueId, name, player)

/** A player left: on Bukkit after the quit event, on a proxy after disconnecting. */
class PlayerLeftEvent(uniqueId: UUID, name: String, player: LibraryPlayer?) :
    PlayerLifecycleEvent(uniqueId, name, player)
