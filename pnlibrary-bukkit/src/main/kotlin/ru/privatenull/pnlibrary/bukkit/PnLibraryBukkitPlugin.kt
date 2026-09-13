package ru.privatenull.pnlibrary.bukkit

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.event.server.PluginEnableEvent
import org.bukkit.plugin.java.JavaPlugin
import ru.privatenull.pnlibrary.bukkit.inventory.MenuService
import ru.privatenull.pnlibrary.bukkit.inventory.MenuServiceImpl
import ru.privatenull.pnlibrary.bukkit.server.ServerInfo
import ru.privatenull.pnlibrary.core.runtime.PnLibraryRuntimeHost
import ru.privatenull.pnlibrary.bukkit.placeholders.PlaceholderApiAdapter

/** Bukkit entry point that owns the pnLibrary runtime and Bukkit-only services. */
class PnLibraryBukkitPlugin : JavaPlugin(), Listener {
    private var runtimeHost: PnLibraryRuntimeHost? = null
    private var menuService: MenuServiceImpl? = null
    private var audienceService: BukkitAudienceService? = null
    private var placeholderApiBridge: AutoCloseable? = null

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
            server.pluginManager.registerEvents(this, this)
            connectPlaceholderApi()
        } catch (error: Throwable) {
            placeholderApiBridge?.close()
            placeholderApiBridge = null
            host.close()
            throw error
        }
    }

    override fun onDisable() {
        menuService?.close()
        menuService = null
        audienceService?.close()
        audienceService = null
        placeholderApiBridge?.close()
        placeholderApiBridge = null
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

    @EventHandler
    fun onPluginEnable(event: PluginEnableEvent) {
        if (event.plugin.name.equals("PlaceholderAPI", ignoreCase = true)) connectPlaceholderApi()
    }

    @EventHandler
    fun onPluginDisable(event: PluginDisableEvent) {
        if (event.plugin.name.equals("PlaceholderAPI", ignoreCase = true)) disconnectPlaceholderApi()
    }

    private fun connectPlaceholderApi() {
        val host = runtimeHost ?: return
        if (placeholderApiBridge != null || !server.pluginManager.isPluginEnabled("PlaceholderAPI")) return
        placeholderApiBridge = host.library.placeholderAdapters.register(PlaceholderApiAdapter(this))
        logger.info("PlaceholderAPI integration connected")
    }

    private fun disconnectPlaceholderApi() {
        placeholderApiBridge?.close()
        placeholderApiBridge = null
        logger.info("PlaceholderAPI integration disconnected")
    }
}
