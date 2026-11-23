package org.pnfolder.pnlibrary.bukkit

import org.bukkit.Server
import org.bukkit.plugin.java.JavaPlugin
import org.pnfolder.pnlibrary.api.logging.PnLogger
import org.pnfolder.pnlibrary.bukkit.api.BukkitPlatform
import org.pnfolder.pnlibrary.bukkit.currency.error
import org.pnfolder.pnlibrary.bukkit.entity.ArmorStandVisual
import org.pnfolder.pnlibrary.bukkit.entity.VisualService
import org.pnfolder.pnlibrary.bukkit.integration.BukkitIntegrations
import org.pnfolder.pnlibrary.bukkit.inventory.BukkitMenus
import org.pnfolder.pnlibrary.bukkit.inventory.MenuService
import org.pnfolder.pnlibrary.bukkit.item.BukkitItems
import org.pnfolder.pnlibrary.bukkit.item.ItemService
import org.pnfolder.pnlibrary.bukkit.player.BukkitPlayerService
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
    private var audiences: BukkitPlayerService? = null
    private var menus: BukkitMenus? = null
    private var integrations: BukkitIntegrations? = null
    private var link: BukkitLinkReporter? = null
    private var logger: PnLogger? = null

    fun start() {
        var startupLogger: PnLogger? = null
        val logBuffer = PnLoggerBuffer(plugin.logger) { startupLogger }
        val playerService = BukkitPlayerService(plugin, logBuffer)
        audiences = playerService
        val network = openLink(plugin)
        var metricsLogger: PnLogger? = null
        val adapter = BukkitPlatformAdapter(plugin, playerService, network, { metricsLogger!! }, logBuffer)
        try {
            val runtime = PnLibraryRuntimeHost.start(plugin, adapter)
            host = runtime
            startupLogger = runtime.library.logging.logger(plugin, "Runtime")
            logBuffer.flush()
            metricsLogger = runtime.library.logging.logger(plugin, "Metrics")
            logger = startupLogger
            installServices(runtime, adapter, playerService)
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
        release("link") { link?.stop() }
        link = null
        release("integrations") { integrations?.stop() }
        integrations = null
        release("menus") { menus?.close() }
        menus = null
        release("audiences") { audiences?.close() }
        audiences = null
        release("library") { host?.close() }
        host = null
        logger = null
    }

    /** One part failing to stop must not keep the others from stopping, but it must not vanish either. */
    private fun release(part: String, action: () -> Unit) {
        runCatchingNonFatal(action).onFailure { logger?.error("Could not stop $part", it) }
    }

    private fun installServices(runtime: PnLibraryRuntimeHost, adapter: BukkitPlatformAdapter, playerService: BukkitPlayerService) {
        val menuService = BukkitMenus(plugin, runtime.library.runtimeTaskScope(), pnLogger = { runtime.library.logging.logger(plugin, "Menus") })
        runtime.registerService(ServerInfo::class.java, adapter.serverInfo)
        runtime.registerService(MenuService::class.java, menuService)
        runtime.registerPlatform(BukkitPlatform::class.java, BukkitPlatformServices(plugin.server, adapter.serverInfo, menuService))
        runtime.registerService(BukkitPlayerService::class.java, playerService)
        menus = menuService
    }
}

/** Native-backed Bukkit platform API installed by the Bukkit runtime. */
private class BukkitPlatformServices(
    override val server: Server,
    override val serverInfo: ServerInfo,
    override val menus: MenuService,
) : BukkitPlatform {
    override val items: ItemService = BukkitItems
    override val visuals: VisualService = ArmorStandVisual.Factory
}
