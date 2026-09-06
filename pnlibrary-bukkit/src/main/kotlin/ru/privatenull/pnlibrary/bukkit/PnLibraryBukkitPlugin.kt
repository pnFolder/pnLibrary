package ru.privatenull.pnlibrary.bukkit

import org.bukkit.plugin.ServicePriority
import org.bukkit.plugin.java.JavaPlugin
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticsService
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.core.runtime.PnLibraryBootstrap
import ru.privatenull.pnlibrary.core.runtime.PnLibraryImpl
import ru.privatenull.pnlibrary.core.updates.MandatoryUpdateService
import ru.privatenull.pnlibrary.bukkit.inventory.MenuService
import ru.privatenull.pnlibrary.bukkit.inventory.MenuServiceImpl
import java.nio.file.Paths

/** The single Bukkit/Paper runtime host installed in the server plugins directory. */
class PnLibraryBukkitPlugin : JavaPlugin() {
    private var runtime: PnLibrary? = null
    private var menus: MenuService? = null

    override fun onEnable() {
        val adapter = BukkitPlatformAdapter(this)
        val loaded = PnLibraryBootstrap.bootstrap(this, adapter)
        adapter.attachLibrary(loaded as PnLibraryImpl)
        server.servicesManager.register(PnLibrary::class.java, loaded, this, ServicePriority.Normal)
        server.servicesManager.register(DiagnosticsService::class.java, loaded.diagnostics, this, ServicePriority.Normal)
        val menuService = MenuServiceImpl(this, loaded.tasks.scope(this))
        server.servicesManager.register(MenuService::class.java, menuService, this, ServicePriority.Normal)
        menus = menuService
        runtime = loaded

        MandatoryUpdateService.start(this, adapter, description.version, "bukkit",
            Paths.get(javaClass.protectionDomain.codeSource.location.toURI()),
            server.updateFolderFile.toPath())

        logger.info("pnLibrary ${description.version} enabled (${adapter.id})")
    }

    override fun onDisable() {
        server.servicesManager.unregisterAll(this)
        runtime?.close()
        menus = null
        runtime = null
    }
}
