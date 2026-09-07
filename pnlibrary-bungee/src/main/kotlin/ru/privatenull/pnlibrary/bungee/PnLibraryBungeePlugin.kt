package ru.privatenull.pnlibrary.bungee

import net.md_5.bungee.api.plugin.Plugin
import ru.privatenull.pnlibrary.core.runtime.PnLibraryRuntimeHost

/** BungeeCord entry point. All shared startup and shutdown logic lives in the runtime host. */
class PnLibraryBungeePlugin : Plugin() {
    private var runtimeHost: PnLibraryRuntimeHost? = null

    override fun onEnable() {
        val adapter = BungeePlatformAdapter(this)
        runtimeHost = PnLibraryRuntimeHost.start(
            this,
            adapter,
            dataFolder.toPath().parent.resolve("update"),
        )
    }

    override fun onDisable() {
        runtimeHost?.close()
        runtimeHost = null
    }
}
