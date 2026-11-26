package org.pnfolder.pnlibrary.bukkit.inventory;

/** Supported Bukkit inventory shapes and their fixed native slot counts. */
public enum MenuType {

    /** Variable chest inventory from one through six rows. */
    CHEST(54),

    /** Three-slot anvil inventory with optional rename text. */
    ANVIL(3),

    HOPPER(5),

    DISPENSER(9),

    DROPPER(9),

    WORKBENCH(10);

    private final int defaultSize;

    MenuType(int defaultSize) {
        this.defaultSize = defaultSize;
    }

    /** Native top-inventory slot count; chest menus replace it with the configured row count times nine. */
    public int getDefaultSize() {
        return defaultSize;
    }
}
