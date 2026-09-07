package ru.privatenull.pnlibrary.bukkit

import org.bukkit.plugin.ServicePriority
import org.bukkit.plugin.java.JavaPlugin
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticsService
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.bukkit.inventory.MenuService
import ru.privatenull.pnlibrary.bukkit.inventory.MenuServiceImpl
import ru.privatenull.pnlibrary.core.runtime.PnLibraryRuntimeHost

/** Bukkit entry point that owns the pnLibrary runtime and Bukkit-only services. */
class PnLibraryBukkitPlugin : JavaPlugin() {
    private var runtimeHost: PnLibraryRuntimeHost? = null
    private var menuService: MenuServiceImpl? = null

    override fun onEnable() {
        val adapter = BukkitPlatformAdapter(this)
        val host = PnLibraryRuntimeHost.start(
            this,
            adapter,
            server.updateFolderFile.toPath(),
        )
        try {
            registerBukkitServices(host.library)
            runtimeHost = host
        } catch (error: Throwable) {
            host.close()
            throw error
        }
    }

    override fun onDisable() {
        menuService?.close()
        menuService = null
        server.servicesManager.unregisterAll(this)
        runtimeHost?.close()
        runtimeHost = null
    }

    private fun registerBukkitServices(library: PnLibrary) {
        server.servicesManager.register(PnLibrary::class.java, library, this, ServicePriority.Normal)
        server.servicesManager.register(DiagnosticsService::class.java, library.diagnostics, this, ServicePriority.Normal)
        val menuService = MenuServiceImpl(this, library.tasks.scope(this))
        server.servicesManager.register(MenuService::class.java, menuService, this, ServicePriority.Normal)
        this.menuService = menuService
    }
}
