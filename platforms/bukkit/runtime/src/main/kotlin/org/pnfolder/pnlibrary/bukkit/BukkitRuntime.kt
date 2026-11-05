package org.pnfolder.pnlibrary.bukkit

import org.bukkit.plugin.java.JavaPlugin
import org.pnfolder.pnlibrary.bukkit.api.BukkitPlatform
import org.pnfolder.pnlibrary.bukkit.inventory.BukkitMenus
import org.pnfolder.pnlibrary.bukkit.inventory.MenuService
import org.pnfolder.pnlibrary.bukkit.server.ServerInfo
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import org.pnfolder.pnlibrary.core.lifecycle.runtimeTaskScope
import org.pnfolder.pnlibrary.core.runtime.PnLibraryRuntimeHost

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
    private var menus: BukkitMenus? = null
    private var integrations: BukkitIntegrations? = null
    private var link: BukkitLinkReporter? = null

    fun start() {
        val audienceService = BukkitAudienceService(plugin)
        audiences = audienceService
        val network = openLink(plugin)
        val adapter = BukkitPlatformAdapter(plugin, audienceService, network)
        try {
            val runtime = PnLibraryRuntimeHost.start(plugin, adapter)
            host = runtime
            installServices(runtime, adapter, audienceService)
            val optional = BukkitIntegrations(plugin)
            integrations = optional
            plugin.server.pluginManager.registerEvents(optional, plugin)
            optional.start(runtime)
            link = BukkitLinkReporter(plugin, runtime.library, network, adapter.scheduler).also { it.start() }
        } catch (error: Throwable) {
            stop()
            throw error
        }
    }

    fun stop() {
        runCatchingNonFatal { link?.stop() }
        link = null
        runCatchingNonFatal { integrations?.stop() }
        integrations = null
        runCatchingNonFatal { menus?.close() }
        menus = null
        runCatchingNonFatal { audiences?.close() }
        audiences = null
        runCatchingNonFatal { host?.close() }
        host = null
    }

    /** Registers Bukkit-only services and the native platform view on the running runtime. */
    private fun installServices(runtime: PnLibraryRuntimeHost, adapter: BukkitPlatformAdapter, audienceService: BukkitAudienceService) {
        val menuService = BukkitMenus(plugin, runtime.library.runtimeTaskScope())
        runtime.registerService(ServerInfo::class.java, adapter.serverInfo)
        runtime.registerService(MenuService::class.java, menuService)
        runtime.registerPlatform(BukkitPlatform::class.java, BukkitPlatformServices(plugin.server, adapter.serverInfo, menuService))
        runtime.registerService(BukkitAudienceService::class.java, audienceService)
        menus = menuService
    }
}
