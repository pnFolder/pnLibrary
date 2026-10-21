package org.pnfolder.pnlibrary.bukkit.api

import org.bukkit.Server
import org.pnfolder.pnlibrary.bukkit.entity.VisualService
import org.pnfolder.pnlibrary.bukkit.inventory.MenuService
import org.pnfolder.pnlibrary.bukkit.item.ItemService
import org.pnfolder.pnlibrary.bukkit.server.ServerInfo

/**
 * Bukkit-only services of pnLibrary. Obtain it with `library.platform<BukkitPlatform>()`.
 *
 * This module holds contracts only; the implementations live in the Bukkit runtime and are installed by it.
 */
interface BukkitPlatform {
    /** Active native Bukkit server. */
    val server: Server

    /** Server implementation and Minecraft version. */
    val serverInfo: ServerInfo

    /** Inventory menus. */
    val menus: MenuService

    /** Items from configuration, player heads and exact item serialization. */
    val items: ItemService

    /** Floating items, blocks and text labels. */
    val visuals: VisualService
}
