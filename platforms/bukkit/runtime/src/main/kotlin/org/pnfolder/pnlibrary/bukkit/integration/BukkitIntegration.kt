package org.pnfolder.pnlibrary.bukkit.integration

import org.bukkit.plugin.java.JavaPlugin
import org.pnfolder.pnlibrary.core.runtime.PnLibraryRuntimeHost

internal class IntegrationContext(val plugin: JavaPlugin, val runtime: PnLibraryRuntimeHost)

// connect() must be idempotent — it is called on every event that might make the integration available.
internal interface BukkitIntegration : AutoCloseable {
    val pluginName: String
    fun connect()
    override fun close()
}
