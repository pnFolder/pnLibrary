package org.pnfolder.pnlibrary.api.events.player

import org.pnfolder.pnlibrary.api.audience.OnlinePlayer
import java.util.UUID

/** A player left: on Bukkit after the quit event, on a proxy after disconnecting. */
class PlayerLeftEvent(uniqueId: UUID, name: String, player: OnlinePlayer?) :
    PlayerLifecycleEvent(uniqueId, name, player)
