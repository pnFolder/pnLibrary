package ru.privatenull.pnlibrary.bukkit

import org.bukkit.Server
import ru.privatenull.pnlibrary.bukkit.api.BukkitPlatform
import ru.privatenull.pnlibrary.bukkit.inventory.MenuService
import ru.privatenull.pnlibrary.bukkit.server.ServerInfo

/** Native-backed Bukkit platform API installed by the Bukkit runtime. */
internal class BukkitPlatformImpl(
    override val server: Server,
    override val serverInfo: ServerInfo,
    override val menus: MenuService,
) : BukkitPlatform
