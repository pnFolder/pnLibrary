package org.pnfolder.pnlibrary.bukkit

import org.pnfolder.pnlibrary.bukkit.placeholders.PlaceholderApiAdapter

/** Registers pnLibrary placeholders with PlaceholderAPI while that plugin is enabled. */
internal class PlaceholderApiIntegration(private val context: IntegrationContext) : BukkitIntegration {
    override val pluginName = "PlaceholderAPI"
    private var registration: AutoCloseable? = null

    override fun connect() {
        if (registration != null || !context.plugin.server.pluginManager.isPluginEnabled(pluginName)) return
        registration = context.runtime.library.placeholderAdapters.register(PlaceholderApiAdapter(context.plugin))
        context.plugin.logger.info("PlaceholderAPI integration connected")
    }

    override fun close() {
        registration?.close()
        registration = null
    }
}
