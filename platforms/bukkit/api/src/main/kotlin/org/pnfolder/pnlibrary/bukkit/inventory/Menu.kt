package org.pnfolder.pnlibrary.bukkit.inventory

/** Handles one click inside a menu's top inventory. */
fun interface MenuClickHandler {

    /** Processes immutable click details and may update or close the session. */
    fun handle(click: MenuClick)
}

/** Receives a normal player-driven menu close notification. */
fun interface MenuCloseHandler {

    /** Processes [close] after the session has been removed from the service. */
    fun handle(close: MenuClose)
}

/** Receives a session after its inventory has opened and initial rendering has completed. */
fun interface MenuOpenHandler {

    /** Processes the newly opened [session]. */
    fun handle(session: MenuSession)
}

/** Rebuilds dynamic inventory contents for a menu session. */
fun interface MenuRenderer {

    /** Renders current application state into [session] through [MenuSession.set]. */
    fun render(session: MenuSession)
}

/** Normal close callback containing the session that just finished. */
data class MenuClose(

    /** Finished session whose inventory remains available for final inspection. */
    val session: MenuSession,
)

/**
 * Immutable menu definition reusable across players and sessions.
 *
 * Static [items] are copied into each new inventory before [renderer] runs. The renderer
 * can then replace them with player-specific or current application state.
 */
data class Menu(

    /** Native Bukkit inventory title. */
    val title: String,

    /** Chest row count; ignored by non-chest menu types. */
    val rows: Int,

    /** Native inventory shape. */
    val type: MenuType,

    /** Static slot definitions keyed by raw top-inventory slot. */
    val items: Map<Int, MenuItem>,

    /** Optional dynamic renderer invoked on open and explicit refresh. */
    val renderer: MenuRenderer?,

    /** Optional callback invoked after initial rendering and opening. */
    val openHandler: MenuOpenHandler?,

    /** Optional callback for normal closes while the owner remains enabled. */
    val closeHandler: MenuCloseHandler?,

    /** Optional handler invoked after a clicked slot's own handler. */
    val clickHandler: MenuClickHandler?,

    /** Whether clicks in the player's lower inventory are cancelled. */
    val cancelPlayerInventory: Boolean,
) {

    /** Effective top-inventory size in slots. */
    val size: Int get() = if (type == MenuType.CHEST) rows * 9 else type.defaultSize
}
