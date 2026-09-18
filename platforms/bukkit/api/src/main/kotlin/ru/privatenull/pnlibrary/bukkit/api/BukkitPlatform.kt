package ru.privatenull.pnlibrary.bukkit.api

import org.bukkit.Server
import ru.privatenull.pnlibrary.bukkit.inventory.MenuService
import ru.privatenull.pnlibrary.bukkit.server.ServerInfo

/** Native Bukkit API and pnLibrary's existing Bukkit-specific services. */
interface BukkitPlatform {
    /** Active native Bukkit server. */
    val server: Server

    /** Normalized server implementation and Minecraft version information. */
    val serverInfo: ServerInfo

    /** Bukkit inventory menu service. */
    val menus: MenuService
}
