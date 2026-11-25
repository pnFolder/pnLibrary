package org.pnfolder.pnlibrary.api.events.player

import org.pnfolder.pnlibrary.api.audience.OnlinePlayer
import java.util.UUID

/** A player finished joining: on Bukkit after the join event, on a proxy after login. */
class PlayerJoinedEvent(uniqueId: UUID, name: String, player: OnlinePlayer?) :
    PlayerLifecycleEvent(uniqueId, name, player)
