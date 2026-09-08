package ru.privatenull.pnlibrary.bukkit.inventory

import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import ru.privatenull.pnlibrary.bukkit.server.PnBukkit

/** Public contract for the Bukkit inventory service provided by pnLibrary. */
interface MenuService {
    fun open(owner: Plugin, player: Player, menu: Menu): MenuSession
    fun session(player: Player): MenuSession?
    fun close(owner: Plugin)
}

/** Entry point for obtaining the installed Bukkit menu service. */
object PnMenus {
    @JvmStatic fun get(): MenuService = PnBukkit.menus()
}
