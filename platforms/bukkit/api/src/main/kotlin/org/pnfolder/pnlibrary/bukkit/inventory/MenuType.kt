package org.pnfolder.pnlibrary.bukkit.inventory

/**
 * Supported Bukkit inventory shapes and their fixed native slot counts.
 *
 * @property defaultSize native top-inventory slot count; chest menus replace
 * this value with the configured row count multiplied by nine
 */
enum class MenuType(val defaultSize: Int) {

    /** Variable chest inventory from one through six rows. */
    CHEST(54),

    /** Three-slot anvil inventory with optional rename text. */
    ANVIL(3),

    /** Five-slot hopper inventory. */
    HOPPER(5),

    /** Nine-slot dispenser inventory. */
    DISPENSER(9),

    /** Nine-slot dropper inventory. */
    DROPPER(9),

    /** Ten-slot crafting workbench inventory. */
    WORKBENCH(10),
}
