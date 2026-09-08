package ru.privatenull.pnlibrary.bukkit.server

import ru.privatenull.pnlibrary.bukkit.inventory.MenuService
import ru.privatenull.pnlibrary.api.runtime.PnLibraryProvider

/** Public Bukkit-specific information supplied by the installed pnLibrary runtime. */
interface BukkitEnvironment {
    val server: ServerInfo
    val menus: MenuService
}

/** Bukkit-only entry point backed by pnLibrary's platform-independent service manager. */
object PnBukkit {
    /** Returns the environment published in pnLibrary's own service manager. */
    @JvmStatic
    fun get(): BukkitEnvironment = PnLibraryProvider.get().services.require(BukkitEnvironment::class.java)

    /** Returns the installed environment, or `null` before startup and after shutdown. */
    @JvmStatic
    fun getOrNull(): BukkitEnvironment? = PnLibraryProvider.getOrNull()?.let { library ->
        runCatching { library.services.get(BukkitEnvironment::class.java) }.getOrNull()
    }

    /** Returns information about the running Bukkit/Paper/Purpur/Leaf/Folia server. */
    @JvmStatic
    fun server(): ServerInfo = get().server

    /** Returns the shared Bukkit inventory service. */
    @JvmStatic
    fun menus(): MenuService = get().menus

}
