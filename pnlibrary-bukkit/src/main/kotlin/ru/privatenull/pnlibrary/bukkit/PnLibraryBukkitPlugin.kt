package ru.privatenull.pnlibrary.bukkit

import org.bukkit.plugin.java.JavaPlugin
import ru.privatenull.pnlibrary.bukkit.inventory.MenuService
import ru.privatenull.pnlibrary.bukkit.inventory.MenuServiceImpl
import ru.privatenull.pnlibrary.bukkit.server.BukkitEnvironment
import ru.privatenull.pnlibrary.bukkit.server.ServerInfo
import ru.privatenull.pnlibrary.api.plugin.PluginId
import ru.privatenull.pnlibrary.api.services.ServiceScope
import ru.privatenull.pnlibrary.core.runtime.PnLibraryRuntimeHost

/** Bukkit entry point that owns the pnLibrary runtime and Bukkit-only services. */
class PnLibraryBukkitPlugin : JavaPlugin() {
    private var runtimeHost: PnLibraryRuntimeHost? = null
    private var menuService: MenuServiceImpl? = null
    private var serviceScope: ServiceScope? = null

    override fun onEnable() {
        val adapter = BukkitPlatformAdapter(this)
        val host = PnLibraryRuntimeHost.start(
            this,
            adapter,
            server.updateFolderFile.toPath(),
        )
        try {
            installBukkitApi(host, adapter)
            runtimeHost = host
        } catch (error: Throwable) {
            host.close()
            throw error
        }
    }

    override fun onDisable() {
        serviceScope?.close()
        serviceScope = null
        menuService?.close()
        menuService = null
        runtimeHost?.close()
        runtimeHost = null
    }

    private fun installBukkitApi(host: PnLibraryRuntimeHost, adapter: BukkitPlatformAdapter) {
        val menuService = MenuServiceImpl(this, host.library.tasks.scope(this))
        val environment = object : BukkitEnvironment {
            override val server: ServerInfo get() = adapter.serverInfo
            override val menus: MenuService get() = menuService
        }
        val serviceScope = host.library.services.scope(PluginId.of("pnlibrary"))
        serviceScope.publish(BukkitEnvironment::class.java, environment)
        this.menuService = menuService
        this.serviceScope = serviceScope
    }
}
