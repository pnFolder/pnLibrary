package ru.privatenull.pnlibrary.bukkit

import org.bukkit.plugin.java.JavaPlugin
import ru.privatenull.pnlibrary.bukkit.inventory.MenuService
import ru.privatenull.pnlibrary.bukkit.inventory.MenuServiceImpl
import ru.privatenull.pnlibrary.bukkit.server.ServerInfo
import ru.privatenull.pnlibrary.core.runtime.PnLibraryRuntimeHost

/** Bukkit entry point that owns the pnLibrary runtime and Bukkit-only services. */
class PnLibraryBukkitPlugin : JavaPlugin() {
    private var runtimeHost: PnLibraryRuntimeHost? = null
    private var menuService: MenuServiceImpl? = null
    private var audienceService: BukkitAudienceService? = null

    override fun onEnable() {
        val adapter = BukkitPlatformAdapter(this)
        val host = PnLibraryRuntimeHost.start(
            this,
            adapter,
            server.updateFolderFile.toPath(),
        )
        try {
            installBukkitServices(host, adapter)
            runtimeHost = host
        } catch (error: Throwable) {
            host.close()
            throw error
        }
    }

    override fun onDisable() {
        menuService?.close()
        menuService = null
        audienceService?.close()
        audienceService = null
        runtimeHost?.close()
        runtimeHost = null
    }

    private fun installBukkitServices(host: PnLibraryRuntimeHost, adapter: BukkitPlatformAdapter) {
        val menuService = MenuServiceImpl(this, host.library.tasks.scope(this))
        host.registerService(ServerInfo::class.java, adapter.serverInfo)
        host.registerService(MenuService::class.java, menuService)
        val audienceService = BukkitAudienceService(this)
        host.registerService(BukkitAudienceService::class.java, audienceService)
        this.audienceService = audienceService
        this.menuService = menuService
    }
}
