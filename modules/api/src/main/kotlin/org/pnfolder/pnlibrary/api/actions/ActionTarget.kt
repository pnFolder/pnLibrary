package org.pnfolder.pnlibrary.api.actions

/** Who a message, title or sound of an action goes to. */
enum class ActionTarget {
    /** The player the scenario runs for. */
    PLAYER,

    /** Everyone the scenario was started for, see [ActionContext.allPlayers]. */
    ALL_PLAYERS,
}
