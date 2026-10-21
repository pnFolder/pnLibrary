package org.pnfolder.pnlibrary.api.actions

/**
 * Built-in audience selectors used by configurable actions.
 */
enum class ActionTarget {
    /** The player that triggered the action execution. */
    PLAYER,

    /** Every player exposed by the current [ActionContext]. */
    ALL_PLAYERS,
}
