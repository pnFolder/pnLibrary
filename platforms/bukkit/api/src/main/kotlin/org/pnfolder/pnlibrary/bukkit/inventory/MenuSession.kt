package org.pnfolder.pnlibrary.bukkit.inventory

import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.time.Duration

/** One player's live view of a reusable [Menu] definition. */
interface MenuSession : AutoCloseable {

    val player: Player

    val menu: Menu

    val inventory: Inventory

    /** Replaces [slot] with [item]; `null` clears it. */
    fun set(slot: Int, item: ItemStack?)

    fun get(slot: Int): ItemStack?

    /** Invokes [Menu.renderer] immediately unless the session has finished. */
    fun refresh()

    /** Schedules [refresh] through the player-aware task scheduler after [delay]. */
    fun refreshAfter(delay: Duration)

    /** Closes this session. Safe to repeat. */
    override fun close()
}
