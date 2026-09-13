package ru.privatenull.pnlibrary.bukkit

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.event.server.PluginEnableEvent
import org.bukkit.event.server.ServiceRegisterEvent
import org.bukkit.event.server.ServiceUnregisterEvent
import org.bukkit.plugin.java.JavaPlugin
import ru.privatenull.pnlibrary.bukkit.inventory.MenuService
import ru.privatenull.pnlibrary.bukkit.inventory.MenuServiceImpl
import ru.privatenull.pnlibrary.bukkit.server.ServerInfo
import ru.privatenull.pnlibrary.core.runtime.PnLibraryRuntimeHost
import ru.privatenull.pnlibrary.bukkit.placeholders.PlaceholderApiAdapter
import ru.privatenull.pnlibrary.bukkit.currency.BukkitCurrencyAdapters
import ru.privatenull.pnlibrary.bukkit.currency.CurrencyCommandExecutor
import ru.privatenull.pnlibrary.api.plugin.PluginId
import ru.privatenull.pnlibrary.api.currency.CurrencyRegistration

/** Bukkit entry point that owns the pnLibrary runtime and Bukkit-only services. */
class PnLibraryBukkitPlugin : JavaPlugin(), Listener {
    private var runtimeHost: PnLibraryRuntimeHost? = null
    private var menuService: MenuServiceImpl? = null
    private var audienceService: BukkitAudienceService? = null
    private var placeholderApiBridge: AutoCloseable? = null
    private val currencyBridges = linkedMapOf<String, CurrencyRegistration>()

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
            getCommand("pncurrency")?.let { command ->
                val executor = CurrencyCommandExecutor(this, host.library.currencyProviders)
                command.setExecutor(executor)
                command.tabCompleter = executor
            }
            connectPlaceholderApi()
            connectCurrencyAdapters()
        } catch (error: Throwable) {
            placeholderApiBridge?.close()
            placeholderApiBridge = null
            currencyBridges.values.forEach { runCatching(it::close) }
            currencyBridges.clear()
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
        currencyBridges.values.forEach { runCatching(it::close) }
        currencyBridges.clear()
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
        connectCurrencyAdapters()
    }

    @EventHandler
    fun onServiceRegister(event: ServiceRegisterEvent) {
        if (event.provider.service.name == "net.milkbowl.vault.economy.Economy") connectCurrencyAdapters()
    }

    @EventHandler
    fun onServiceUnregister(event: ServiceUnregisterEvent) {
        if (event.provider.service.name == "net.milkbowl.vault.economy.Economy") disconnectCurrency("vault")
    }

    @EventHandler
    fun onPluginDisable(event: PluginDisableEvent) {
        if (event.plugin.name.equals("PlaceholderAPI", ignoreCase = true)) disconnectPlaceholderApi()
        if (event.plugin.name.equals("Vault", ignoreCase = true)) disconnectCurrency("vault")
        if (event.plugin.name.equals("PlayerPoints", ignoreCase = true)) disconnectCurrency("playerpoints")
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

    private fun disconnectCurrency(id: String) {
        currencyBridges.remove(id)?.close()
        logger.info("${if (id == "vault") "Vault" else "PlayerPoints"} currency disconnected")
    }
}
