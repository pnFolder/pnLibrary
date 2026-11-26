package org.pnfolder.pnlibrary.bukkit.inventory;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Creates the Bukkit inventory behind a menu; replaced in tests. */
@FunctionalInterface
interface MenuInventories {
    Inventory create(Menu menu, InventoryHolder holder);
}
