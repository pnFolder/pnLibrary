package org.pnfolder.pnlibrary.bukkit

import org.bukkit.plugin.java.JavaPlugin
import org.pnfolder.pnlibrary.api.currency.currencyProviders
import org.pnfolder.pnlibrary.bukkit.api.BukkitPlatform
import org.pnfolder.pnlibrary.bukkit.inventory.MenuService
import org.pnfolder.pnlibrary.bukkit.inventory.MenuServiceImpl
import org.pnfolder.pnlibrary.bukkit.server.ServerInfo
import org.pnfolder.pnlibrary.core.runtime.PnLibraryRuntimeHost
import org.pnfolder.pnlibrary.core.runtime.lifecycle.runtimeTaskScope

/**
 * Owns everything the Bukkit plugin starts: the shared runtime, Bukkit services and optional
 * integrations.
 *
 * [start] is transactional: a failure stops whatever was already created before the exception is
 * rethrown to Bukkit. [stop] releases every resource and is safe to call more than once.
 */
internal class BukkitRuntime(private val plugin: JavaPlugin) {
    private var host: PnLibraryRuntimeHost? = null
    private var audiences: BukkitAudienceService? = null
    private var menus: MenuServiceImpl? = null
    private var integrations: BukkitIntegrations? = null

    fun start() {
        val audienceService = BukkitAudienceService(plugin)
        audiences = audienceService
        val adapter = BukkitPlatformAdapter(plugin, audienceService)
        try {
            val runtime = PnLibraryRuntimeHost.start(plugin, adapter)
            host = runtime
            installServices(runtime, adapter, audienceService)
            val optional = BukkitIntegrations(plugin)
            integrations = optional
            plugin.server.pluginManager.registerEvents(optional, plugin)
            optional.start(runtime)
        } catch (error: Throwable) {
            stop()
            throw error
        }
    }

    fun stop() {
        runCatching { integrations?.stop() }
        integrations = null
        runCatching { menus?.close() }
        menus = null
        runCatching { audiences?.close() }
        audiences = null
        runCatching { host?.close() }
        host = null
    }

    /** Registers Bukkit-only services and the native platform view on the running runtime. */
    private fun installServices(runtime: PnLibraryRuntimeHost, adapter: BukkitPlatformAdapter, audienceService: BukkitAudienceService) {
        val menuService = MenuServiceImpl(plugin, runtime.library.runtimeTaskScope())
        runtime.registerService(ServerInfo::class.java, adapter.serverInfo)
        runtime.registerService(MenuService::class.java, menuService)
        runtime.registerPlatform(BukkitPlatform::class.java, BukkitPlatformImpl(plugin.server, adapter.serverInfo, menuService))
        runtime.registerService(BukkitAudienceService::class.java, audienceService)
        menus = menuService
    }
}
