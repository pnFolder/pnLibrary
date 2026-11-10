package org.pnfolder.pnlibrary.bukkit.inventory

import org.bukkit.Bukkit
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import java.util.UUID

/** Creates the Bukkit inventory behind a menu; replaced in tests. */
internal fun interface MenuInventories {
    fun create(menu: Menu, holder: InventoryHolder): Inventory
}

/** Creates real server inventories. */
internal object NativeMenuInventories : MenuInventories {
    @Suppress("DEPRECATION")
    override fun create(menu: Menu, holder: InventoryHolder): Inventory =
        if (menu.type == MenuType.CHEST) {
            Bukkit.createInventory(holder, menu.size, menu.title)
        } else {
            Bukkit.createInventory(holder, InventoryType.valueOf(menu.type.name), menu.title)
        }
}

/** Marks a pnLibrary inventory, so menus are recognized by identity rather than by title. */
internal class MenuHolder(val id: UUID) : InventoryHolder {
    private var inventory: Inventory? = null

    /** Remembers the inventory created for this holder. */
    fun bind(inventory: Inventory) {
        this.inventory = inventory
    }

    override fun getInventory(): Inventory = checkNotNull(inventory) { "Menu inventory is not created yet" }
}

/** Text typed into an anvil: `getRenameText` exists only on newer servers, older ones expose the item name. */
@Suppress("DEPRECATION")
internal fun Inventory.anvilRenameText(): String? = try {
    val method = javaClass.methods.firstOrNull { it.name == "getRenameText" && it.parameterTypes.isEmpty() }
    (method?.invoke(this) as? String)?.takeIf(String::isNotBlank)
        ?: getItem(2)?.itemMeta?.displayName
        ?: getItem(0)?.itemMeta?.displayName
} catch (_: ReflectiveOperationException) {
    // The accessor is missing on older servers; the item name is read instead.
    getItem(2)?.itemMeta?.displayName ?: getItem(0)?.itemMeta?.displayName
}
