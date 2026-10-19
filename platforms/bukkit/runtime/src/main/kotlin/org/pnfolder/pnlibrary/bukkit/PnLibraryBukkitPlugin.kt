package org.pnfolder.pnlibrary.bukkit

import org.bukkit.plugin.java.JavaPlugin
import org.pnfolder.pnlibrary.api.currency.currencyProviders
import org.pnfolder.pnlibrary.bukkit.api.BukkitPlatform
import org.pnfolder.pnlibrary.bukkit.inventory.MenuService
import org.pnfolder.pnlibrary.bukkit.inventory.MenuServiceImpl
import org.pnfolder.pnlibrary.bukkit.currency.CurrencyCommandExecutor
import org.pnfolder.pnlibrary.bukkit.server.ServerInfo
import org.pnfolder.pnlibrary.core.runtime.PnLibraryRuntimeHost
import org.pnfolder.pnlibrary.core.runtime.registerRuntimeCommand
import org.pnfolder.pnlibrary.core.runtime.runtimeTaskScope

/**
 * Bukkit entry point for pnLibrary.
 *
 * Startup is transactional: if a service or integration fails, everything created earlier in the
 * attempt is closed before the exception returns to Bukkit. Optional integrations live in
 * [BukkitIntegrations] and follow the enable and disable state of their plugins.
 */
class PnLibraryBukkitPlugin : JavaPlugin() {
    private var runtimeHost: PnLibraryRuntimeHost? = null
    private var audienceService: BukkitAudienceService? = null
    private var menuService: MenuServiceImpl? = null
    private var integrations: BukkitIntegrations? = null

    /** Starts the shared runtime, installs Bukkit services and connects optional integrations. */
    override fun onEnable() {
        val audiences = BukkitAudienceService(this).also { audienceService = it }
        val adapter = BukkitPlatformAdapter(this, audiences)
        try {
            val host = PnLibraryRuntimeHost.start(this, adapter)
            runtimeHost = host
            installBukkitServices(host, adapter)
            val optional = BukkitIntegrations(this).also { integrations = it }
            server.pluginManager.registerEvents(optional, this)
            optional.start(host)
            host.library.registerRuntimeCommand(CurrencyCommandExecutor(this, host.library.currencyProviders).definition())
        } catch (error: Throwable) {
            closePlatformResources()
            throw error
        }
    }

    /** Releases integrations, Bukkit services and the runtime. */
    override fun onDisable() {
        closePlatformResources()
    }

    /** Registers Bukkit-only services and the native platform view. */
    private fun installBukkitServices(host: PnLibraryRuntimeHost, adapter: BukkitPlatformAdapter) {
        val menus = MenuServiceImpl(this, host.library.runtimeTaskScope())
        host.registerService(ServerInfo::class.java, adapter.serverInfo)
        host.registerService(MenuService::class.java, menus)
        host.registerPlatform(BukkitPlatform::class.java, BukkitPlatformImpl(server, adapter.serverInfo, menus))
        host.registerService(BukkitAudienceService::class.java, requireNotNull(audienceService))
        menuService = menus
    }

    /** Closes resources in reverse order of creation; each step runs even if an earlier one fails. */
    private fun closePlatformResources() {
        runCatching { integrations?.stop() }
        integrations = null
        runCatching { menuService?.close() }
        menuService = null
        runCatching { audienceService?.close() }
        audienceService = null
        runCatching { runtimeHost?.close() }
        runtimeHost = null
    }
}
