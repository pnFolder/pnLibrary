package org.pnfolder.pnlibrary.bukkit.inventory;

import org.bukkit.Bukkit;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Creates real server inventories. */
final class NativeMenuInventories implements MenuInventories {
    static final NativeMenuInventories INSTANCE = new NativeMenuInventories();

    private NativeMenuInventories() {
    }

    @Override
    @SuppressWarnings("deprecation")
    public Inventory create(Menu menu, InventoryHolder holder) {
        if (menu.getType() == MenuType.CHEST) {
            return Bukkit.createInventory(holder, menu.getSize(), menu.getTitle());
        }
        return Bukkit.createInventory(holder, InventoryType.valueOf(menu.getType().name()), menu.getTitle());
    }
}
