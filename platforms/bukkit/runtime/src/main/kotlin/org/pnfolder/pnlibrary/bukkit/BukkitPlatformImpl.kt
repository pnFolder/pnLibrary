package org.pnfolder.pnlibrary.bukkit

import org.bukkit.Server
import org.pnfolder.pnlibrary.bukkit.api.BukkitPlatform
import org.pnfolder.pnlibrary.bukkit.entity.ArmorStandVisual
import org.pnfolder.pnlibrary.bukkit.entity.VisualService
import org.pnfolder.pnlibrary.bukkit.inventory.MenuService
import org.pnfolder.pnlibrary.bukkit.item.ItemService
import org.pnfolder.pnlibrary.bukkit.item.ItemServiceImpl
import org.pnfolder.pnlibrary.bukkit.server.ServerInfo

/** Native-backed Bukkit platform API installed by the Bukkit runtime. */
internal class BukkitPlatformImpl(
    override val server: Server,
    override val serverInfo: ServerInfo,
    override val menus: MenuService,
) : BukkitPlatform {
    override val items: ItemService = ItemServiceImpl
    override val visuals: VisualService = ArmorStandVisual.Factory
}
