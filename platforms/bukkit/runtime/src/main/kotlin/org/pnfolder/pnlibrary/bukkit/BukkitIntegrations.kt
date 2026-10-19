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
import org.pnfolder.pnlibrary.bukkit.currency.BukkitCurrencyAdapters
import org.pnfolder.pnlibrary.bukkit.placeholders.PlaceholderApiAdapter
import org.pnfolder.pnlibrary.core.runtime.PnLibraryRuntimeHost

/**
 * Optional Bukkit integrations: PlaceholderAPI, Vault and PlayerPoints.
 *
 * Each integration connects when its plugin is enabled and disconnects when it is disabled, so the
 * runtime keeps working without a restart. [start] connects what is already available, [stop] removes
 * every connection and unregisters the event listener.
 */
internal class BukkitIntegrations(private val plugin: JavaPlugin) : Listener {
    private var host: PnLibraryRuntimeHost? = null
    private var placeholderBridge: AutoCloseable? = null
    private val currencyBridges = linkedMapOf<String, CurrencyRegistration>()

    /** Connects every integration that is already available for [runtime]. */
    fun start(runtime: PnLibraryRuntimeHost) {
        host = runtime
        connectPlaceholderApi()
        connectCurrencyAdapters()
    }

    /** Disconnects every integration and stops listening to plugin events. */
    fun stop() {
        HandlerList.unregisterAll(this)
        disconnectPlaceholderApi()
        currencyBridges.keys.toList().forEach(::disconnectCurrency)
        host = null
    }

    @EventHandler
    fun onPluginEnable(event: PluginEnableEvent) {
        if (event.plugin.name.equals("PlaceholderAPI", ignoreCase = true)) connectPlaceholderApi()
        connectCurrencyAdapters()
    }

    @EventHandler
    fun onServiceRegister(event: ServiceRegisterEvent) {
        if (event.provider.service.name == VAULT_ECONOMY) connectCurrencyAdapters()
    }

    @EventHandler
    fun onServiceUnregister(event: ServiceUnregisterEvent) {
        if (event.provider.service.name == VAULT_ECONOMY) disconnectCurrency(VAULT)
    }

    @EventHandler
    fun onPluginDisable(event: PluginDisableEvent) {
        if (event.plugin.name.equals("PlaceholderAPI", ignoreCase = true)) disconnectPlaceholderApi()
        if (event.plugin.name.equals("Vault", ignoreCase = true)) disconnectCurrency(VAULT)
        if (event.plugin.name.equals("PlayerPoints", ignoreCase = true)) disconnectCurrency(PLAYER_POINTS)
    }

    private fun connectPlaceholderApi() {
        val runtime = host ?: return
        if (placeholderBridge != null || !plugin.server.pluginManager.isPluginEnabled("PlaceholderAPI")) return
        placeholderBridge = runtime.library.placeholderAdapters.register(PlaceholderApiAdapter(plugin))
        plugin.logger.info("PlaceholderAPI integration connected")
    }

    private fun disconnectPlaceholderApi() {
        placeholderBridge?.close()
        placeholderBridge = null
    }

    private fun connectCurrencyAdapters() {
        val runtime = host?.library ?: return
        if (VAULT !in currencyBridges && plugin.server.pluginManager.isPluginEnabled("Vault")) {
            BukkitCurrencyAdapters.vault(plugin)?.let { provider ->
                currencyBridges[VAULT] = runtime.currencyProviders.register(PluginId.of(VAULT), "money", provider)
                plugin.logger.info("Vault currency connected as vault:money")
            }
        }
        if (PLAYER_POINTS !in currencyBridges && plugin.server.pluginManager.isPluginEnabled("PlayerPoints")) {
            BukkitCurrencyAdapters.playerPoints(plugin)?.let { provider ->
                currencyBridges[PLAYER_POINTS] =
                    runtime.currencyProviders.register(PluginId.of(PLAYER_POINTS), "points", provider)
                plugin.logger.info("PlayerPoints currency connected as playerpoints:points")
            }
        }
    }

    private fun disconnectCurrency(id: String) {
        currencyBridges.remove(id)?.let { registration ->
            runCatching(registration::close)
            plugin.logger.info("${if (id == VAULT) "Vault" else "PlayerPoints"} currency disconnected")
        }
    }

    private companion object {
        const val VAULT = "vault"
        const val PLAYER_POINTS = "playerpoints"
        const val VAULT_ECONOMY = "net.milkbowl.vault.economy.Economy"
    }
}
