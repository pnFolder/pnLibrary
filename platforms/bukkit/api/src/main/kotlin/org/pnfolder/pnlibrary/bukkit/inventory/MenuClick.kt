package org.pnfolder.pnlibrary.bukkit.inventory

import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.inventory.ItemStack

/**
 * Immutable details of one top-inventory click.
 *
 * A slot-specific [MenuItem.handler] runs before [Menu.clickHandler]. Non-editable slots
 * are cancelled before either handler runs. Item stacks reflect the Bukkit event and
 * should be cloned by handlers that retain or mutate them asynchronously.
 *
 * @property session active session receiving the event
 * @property slot raw zero-based slot in the top inventory
 * @property item item occupying the clicked slot, if any
 * @property cursor item currently held by the cursor, if any
 * @property click physical Bukkit click type
 * @property action inventory action inferred by Bukkit
 * @property topInventory always true for dispatched menu clicks
 * @property renameText current nonblank anvil input when available
 */
data class MenuClick(
    val session: MenuSession,
    val slot: Int,
    val item: ItemStack?,
    val cursor: ItemStack?,
    val click: ClickType,
    val action: InventoryAction,
    val topInventory: Boolean,
    val renameText: String? = null,
) {

    /** Player associated with [session]. */
    val player: Player get() = session.player

    /** Closes the active session. */
    fun close() = session.close()

    /** Immediately invokes the configured renderer when the session is still active. */
    fun refresh() = session.refresh()
}
