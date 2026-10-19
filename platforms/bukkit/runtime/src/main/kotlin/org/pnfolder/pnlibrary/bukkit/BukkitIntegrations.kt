package org.pnfolder.pnlibrary.bukkit

import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.event.server.PluginEnableEvent
import org.bukkit.event.server.ServiceRegisterEvent
import org.bukkit.event.server.ServiceUnregisterEvent
import org.bukkit.plugin.java.JavaPlugin
import org.pnfolder.pnlibrary.bukkit.currency.BukkitCurrencyAdapters
import org.pnfolder.pnlibrary.core.runtime.PnLibraryRuntimeHost

/**
 * Connects optional integrations as their plugins come and go, without a server restart.
 *
 * Every event is cheap: [BukkitIntegration.connect] checks availability and does nothing when the
 * integration is already connected.
 */
internal class BukkitIntegrations(private val plugin: JavaPlugin) : Listener {
    private var integrations: List<BukkitIntegration> = emptyList()

    /** Creates the integrations for [runtime] and connects those already available. */
    fun start(runtime: PnLibraryRuntimeHost) {
        val context = IntegrationContext(plugin, runtime)
        integrations = listOf(
            PlaceholderApiIntegration(context),
            CurrencyIntegration(context, "Vault", "vault", "money", BukkitCurrencyAdapters::vault),
            CurrencyIntegration(context, "PlayerPoints", "playerpoints", "points", BukkitCurrencyAdapters::playerPoints),
        )
        connectAll()
    }

    /** Disconnects every integration and stops listening to plugin events. */
    fun stop() {
        HandlerList.unregisterAll(this)
        integrations.forEach(BukkitIntegration::close)
        integrations = emptyList()
    }

    @EventHandler
    fun onPluginEnable(event: PluginEnableEvent) = connectAll()

    @EventHandler
    fun onServiceRegister(event: ServiceRegisterEvent) = connectAll()

    @EventHandler
    fun onServiceUnregister(event: ServiceUnregisterEvent) {
        if (event.provider.service.name == VAULT_ECONOMY) integration("Vault")?.close()
    }

    @EventHandler
    fun onPluginDisable(event: PluginDisableEvent) {
        integration(event.plugin.name)?.close()
    }

    private fun connectAll() = integrations.forEach(BukkitIntegration::connect)

    private fun integration(pluginName: String): BukkitIntegration? =
        integrations.firstOrNull { it.pluginName.equals(pluginName, ignoreCase = true) }

    private companion object {
        const val VAULT_ECONOMY = "net.milkbowl.vault.economy.Economy"
    }
}
