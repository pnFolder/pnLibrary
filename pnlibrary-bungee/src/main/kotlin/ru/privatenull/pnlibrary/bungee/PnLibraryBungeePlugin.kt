package ru.privatenull.pnlibrary.bungee

import net.md_5.bungee.api.plugin.Plugin
import ru.privatenull.pnlibrary.core.runtime.PnLibraryRuntimeHost

/**
 * BungeeCord lifecycle entry point for pnLibrary.
 *
 * Shared initialization, platform binding, update monitoring, and rollback live in
 * [PnLibraryRuntimeHost]. The host is retained only while the plugin is enabled and is closed
 * idempotently during shutdown.
 */
class PnLibraryBungeePlugin : Plugin() {
    private var runtimeHost: PnLibraryRuntimeHost? = null

    /** Creates and starts the shared runtime after BungeeCord enables the plugin. */
    override fun onEnable() {
        val adapter = BungeePlatformAdapter(this)
        runtimeHost = PnLibraryRuntimeHost.start(
            this,
            adapter,
            dataFolder.toPath().parent.resolve("update"),
        )
    }

    /** Closes the runtime and releases its scheduler, registrations, and integrations. */
    override fun onDisable() {
        runtimeHost?.close()
        runtimeHost = null
    }
}
