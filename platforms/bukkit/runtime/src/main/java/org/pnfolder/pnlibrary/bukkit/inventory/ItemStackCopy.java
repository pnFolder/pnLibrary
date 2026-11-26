package org.pnfolder.pnlibrary.bukkit.inventory;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Puts clones of the static items of a menu into an inventory. */
final class ItemStackCopy {
    private ItemStackCopy() {
    }

    static void place(Inventory inventory, int slot, ItemStack item) {
        inventory.setItem(slot, item == null ? null : item.clone());
    }
}
