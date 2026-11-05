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
    val server: Server

    val serverInfo: ServerInfo

    val menus: MenuService

    val items: ItemService

    val visuals: VisualService
}
