package org.pnfolder.pnlibrary.bukkit.inventory

import org.bukkit.inventory.ItemStack

/**
 * Immutable initial definition and click behavior for one menu slot.
 *
 * @property item cloned initial item, or `null` for an empty slot
 * @property handler slot-specific click handler, or `null`
 * @property editable whether Bukkit may apply player inventory actions to this slot
 */
data class MenuItem(
    val item: ItemStack?,
    val handler: MenuClickHandler?,
    val editable: Boolean,
)
