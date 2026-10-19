package org.pnfolder.pnlibrary.bukkit

import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.api.currency.CurrencyProvider
import org.pnfolder.pnlibrary.api.currency.CurrencyRegistration
import org.pnfolder.pnlibrary.api.currency.currencyProviders
import org.pnfolder.pnlibrary.api.plugin.PluginId

/**
 * Exposes an external economy plugin (Vault, PlayerPoints, ...) as a pnLibrary currency.
 *
 * [createProvider] returns `null` when the plugin is present but its economy service is not ready,
 * in which case the integration stays disconnected and is retried on the next event.
 */
internal class CurrencyIntegration(
    private val context: IntegrationContext,
    override val pluginName: String,
    private val providerId: String,
    private val currencyName: String,
    private val createProvider: (Plugin) -> CurrencyProvider?,
) : BukkitIntegration {
    private var registration: CurrencyRegistration? = null

    override fun connect() {
        if (registration != null || !context.plugin.server.pluginManager.isPluginEnabled(pluginName)) return
        val provider = createProvider(context.plugin) ?: return
        registration = context.runtime.library.currencyProviders.register(PluginId.of(providerId), currencyName, provider)
        context.plugin.logger.info("$pluginName currency connected as $providerId:$currencyName")
    }

    override fun close() {
        registration?.let { runCatching(it::close) }
        registration = null
    }
}
