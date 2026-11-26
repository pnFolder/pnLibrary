package org.pnfolder.pnlibrary.bukkit.inventory;

import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Marks a pnLibrary inventory, so menus are recognized by identity rather than by title. */
final class MenuHolder implements InventoryHolder {
    private final UUID id;
    private Inventory inventory;

    MenuHolder(UUID id) {
        this.id = id;
    }

    UUID getId() {
        return id;
    }

    /** Remembers the inventory created for this holder. */
    void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        if (inventory == null) {
            throw new IllegalStateException("Menu inventory is not created yet");
        }
        return inventory;
    }
}
