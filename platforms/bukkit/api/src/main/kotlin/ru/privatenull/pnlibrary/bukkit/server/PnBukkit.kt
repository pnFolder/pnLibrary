package ru.privatenull.pnlibrary.bukkit.server

import ru.privatenull.pnlibrary.api.runtime.PnLibraryProvider
import ru.privatenull.pnlibrary.api.platform.platform
import ru.privatenull.pnlibrary.bukkit.api.BukkitPlatform
import ru.privatenull.pnlibrary.bukkit.inventory.MenuService

/** Simple access to Bukkit-specific services supplied by pnLibrary. */
@Deprecated(
    message = "Use PnLibraryProvider.get().platform<BukkitPlatform>() instead",
    level = DeprecationLevel.WARNING,
)
object PnBukkit {
    /** Returns information about the running Bukkit/Paper/Purpur/Leaf/Folia server. */
    @JvmStatic
    fun server(): ServerInfo = PnLibraryProvider.get().platform<BukkitPlatform>().serverInfo

    /** Returns the shared Bukkit inventory service. */
    @JvmStatic
    fun menus(): MenuService = PnLibraryProvider.get().platform<BukkitPlatform>().menus
}
