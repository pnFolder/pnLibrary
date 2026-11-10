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
    /** The running Bukkit server. */
    val server: Server

    /** Name and version of the server software, resolved once at startup. */
    val serverInfo: ServerInfo

    /** Opens and tracks inventory menus. */
    val menus: MenuService

    /** Creates, reads and writes items. */
    val items: ItemService

    /** Spawns floating items, blocks and text. */
    val visuals: VisualService
}
