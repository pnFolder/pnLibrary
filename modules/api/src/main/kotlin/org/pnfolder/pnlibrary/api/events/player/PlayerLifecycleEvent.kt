package org.pnfolder.pnlibrary.api.events.player

import org.pnfolder.pnlibrary.api.events.Event
import org.pnfolder.pnlibrary.api.audience.OnlinePlayer
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
    val player: OnlinePlayer?,
) : Event()
