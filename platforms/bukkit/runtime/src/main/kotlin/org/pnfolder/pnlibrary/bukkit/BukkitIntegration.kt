package org.pnfolder.pnlibrary.bukkit

import org.bukkit.plugin.java.JavaPlugin
import org.pnfolder.pnlibrary.core.runtime.PnLibraryRuntimeHost

/** Everything an optional integration needs from the running plugin. */
internal class IntegrationContext(val plugin: JavaPlugin, val runtime: PnLibraryRuntimeHost)

/**
 * One optional Bukkit plugin that pnLibrary can use when it is present.
 *
 * [pluginName] is the Bukkit plugin whose enable and disable events drive this integration.
 * [connect] must be idempotent: it is called on every event that might make the integration available.
 */
internal interface BukkitIntegration : AutoCloseable {
    val pluginName: String

    /** Connects when the optional plugin is available. Does nothing when already connected. */
    fun connect()

    /** Disconnects and releases the registration. Safe to call when not connected. */
    override fun close()
}
