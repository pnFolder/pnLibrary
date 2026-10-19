package org.pnfolder.pnlibrary.bukkit

import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.event.server.PluginEnableEvent
import org.bukkit.event.server.ServiceRegisterEvent
import org.bukkit.event.server.ServiceUnregisterEvent
import org.bukkit.plugin.java.JavaPlugin
import org.pnfolder.pnlibrary.api.currency.CurrencyRegistration
import org.pnfolder.pnlibrary.api.currency.currencyProviders
import org.pnfolder.pnlibrary.api.plugin.PluginId
import org.pnfolder.pnlibrary.bukkit.api.BukkitPlatform
import org.pnfolder.pnlibrary.bukkit.currency.BukkitCurrencyAdapters
import org.pnfolder.pnlibrary.bukkit.currency.CurrencyCommandExecutor
import org.pnfolder.pnlibrary.bukkit.inventory.MenuService
import org.pnfolder.pnlibrary.bukkit.inventory.MenuServiceImpl
import org.pnfolder.pnlibrary.bukkit.placeholders.PlaceholderApiAdapter
import org.pnfolder.pnlibrary.bukkit.server.ServerInfo
import org.pnfolder.pnlibrary.core.runtime.PnLibraryRuntimeHost
import org.pnfolder.pnlibrary.core.runtime.registerRuntimeCommand
import org.pnfolder.pnlibrary.core.runtime.runtimeTaskScope

/**
 * Bukkit plugin entry point and lifecycle owner for the shared runtime and Bukkit-only services.
 *
 * Startup is transactional: if a native service or optional integration fails, every resource
 * created earlier in the attempt is closed before the exception returns to Bukkit. Runtime plugin
 * enable/disable events reconnect PlaceholderAPI, Vault, and PlayerPoints without a server restart.
 */
class PnLibraryBukkitPlugin : JavaPlugin(), Listener {
    private var runtimeHost: PnLibraryRuntimeHost? = null
    private var menuService: MenuServiceImpl? = null
    private var audienceService: BukkitAudienceService? = null
    private var placeholderApiBridge: AutoCloseable? = null
    private val currencyBridges = linkedMapOf<String, CurrencyRegistration>()

    /**
     * Starts the shared runtime and installs Bukkit services and optional integrations.
     *
     * Any failure rolls back resources created earlier in this enable attempt before
     * propagating the original exception to Bukkit.
     */
    override fun onEnable() {
        val audienceService = BukkitAudienceService(this)
        this.audienceService = audienceService
        val adapter = BukkitPlatformAdapter(this, audienceService)
        try {
            val host = PnLibraryRuntimeHost.start(
                this,
                adapter,
            )
            runtimeHost = host
            installBukkitServices(host, adapter)
            server.pluginManager.registerEvents(this, this)
            val currencyCommand = CurrencyCommandExecutor(this, host.library.currencyProviders)
            host.library.registerRuntimeCommand(currencyCommand.definition())
            connectPlaceholderApi()
            connectCurrencyAdapters()
        } catch (error: Throwable) {
            closePlatformResources()
            throw error
        }
    }

    /** Disconnects native integrations and closes every service owned by this plugin. */
    override fun onDisable() {
        closePlatformResources()
    }

    /** Registers Bukkit-only services and the native platform view. */
    private fun installBukkitServices(host: PnLibraryRuntimeHost, adapter: BukkitPlatformAdapter) {
        val menuService = MenuServiceImpl(this, host.library.runtimeTaskScope())
        host.registerService(ServerInfo::class.java, adapter.serverInfo)
        host.registerService(MenuService::class.java, menuService)
        host.registerPlatform(
            BukkitPlatform::class.java,
            BukkitPlatformImpl(server, adapter.serverInfo, menuService),
        )
        host.registerService(BukkitAudienceService::class.java, requireNotNull(audienceService))
        this.menuService = menuService
    }

    /** Re-evaluates optional plugin integrations after any Bukkit plugin is enabled. */
    @EventHandler
    fun onPluginEnable(event: PluginEnableEvent) {
        if (event.plugin.name.equals("PlaceholderAPI", ignoreCase = true)) connectPlaceholderApi()
        connectCurrencyAdapters()
    }

    /** Connects Vault currency support when its economy service becomes available. */
    @EventHandler
    fun onServiceRegister(event: ServiceRegisterEvent) {
        if (event.provider.service.name == "net.milkbowl.vault.economy.Economy") connectCurrencyAdapters()
    }

    /** Removes Vault currency support before its economy provider becomes stale. */
    @EventHandler
    fun onServiceUnregister(event: ServiceUnregisterEvent) {
        if (event.provider.service.name == "net.milkbowl.vault.economy.Economy") disconnectCurrency("vault")
    }

    /** Disconnects integrations owned by an optional plugin before it unloads. */
    @EventHandler
    fun onPluginDisable(event: PluginDisableEvent) {
        if (event.plugin.name.equals("PlaceholderAPI", ignoreCase = true)) disconnectPlaceholderApi()
        if (event.plugin.name.equals("Vault", ignoreCase = true)) disconnectCurrency("vault")
        if (event.plugin.name.equals("PlayerPoints", ignoreCase = true)) disconnectCurrency("playerpoints")
    }

    /** Connects PlaceholderAPI only when the optional plugin is enabled. */
    private fun connectPlaceholderApi() {
        val host = runtimeHost ?: return
        if (placeholderApiBridge != null || !server.pluginManager.isPluginEnabled("PlaceholderAPI")) return
        placeholderApiBridge = host.library.placeholderAdapters.register(PlaceholderApiAdapter(this))
        logger.info("PlaceholderAPI integration connected")
    }

    /** Releases the current PlaceholderAPI registration. */
    private fun disconnectPlaceholderApi() {
        placeholderApiBridge?.close()
        placeholderApiBridge = null
        logger.info("PlaceholderAPI integration disconnected")
    }

    /** Connects available Vault and PlayerPoints providers once. */
    private fun connectCurrencyAdapters() {
        val library = runtimeHost?.library ?: return
        if ("vault" !in currencyBridges && server.pluginManager.isPluginEnabled("Vault")) {
            BukkitCurrencyAdapters.vault(this)?.let { provider ->
                currencyBridges["vault"] = library.currencyProviders.register(PluginId.of("vault"), "money", provider)
                logger.info("Vault currency connected as vault:money")
            }
        }
        if ("playerpoints" !in currencyBridges && server.pluginManager.isPluginEnabled("PlayerPoints")) {
            BukkitCurrencyAdapters.playerPoints(this)?.let { provider ->
                currencyBridges["playerpoints"] = library.currencyProviders.register(PluginId.of("playerpoints"), "points", provider)
                logger.info("PlayerPoints currency connected as playerpoints:points")
            }
        }
    }

    /** Releases the selected optional currency provider. */
    private fun disconnectCurrency(id: String) {
        currencyBridges.remove(id)?.close()
        logger.info("${if (id == "vault") "Vault" else "PlayerPoints"} currency disconnected")
    }

    /** Releases optional integrations and native services during shutdown or startup rollback. */
    private fun closePlatformResources() {
        HandlerList.unregisterAll(this as Listener)
        runCatching { placeholderApiBridge?.close() }
        placeholderApiBridge = null

        currencyBridges.values.forEach { registration ->
            runCatching(registration::close)
        }
        currencyBridges.clear()

        runCatching { menuService?.close() }
        menuService = null
        runCatching { audienceService?.close() }
        audienceService = null

        runCatching { runtimeHost?.close() }
        runtimeHost = null
    }
}
