package org.pnfolder.pnlibrary.bukkit.api

import org.bukkit.Server
import org.pnfolder.pnlibrary.bukkit.inventory.MenuService
import org.pnfolder.pnlibrary.bukkit.server.ServerInfo

/** Native Bukkit API and pnLibrary's existing Bukkit-specific services. */
interface BukkitPlatform {
    /** Active native Bukkit server. */
    val server: Server

    /** Normalized server implementation and Minecraft version information. */
    val serverInfo: ServerInfo

    /** Bukkit inventory menu service. */
    val menus: MenuService
}
