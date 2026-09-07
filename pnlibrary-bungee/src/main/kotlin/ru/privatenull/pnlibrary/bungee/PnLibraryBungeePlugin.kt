package ru.privatenull.pnlibrary.bungee

import net.md_5.bungee.api.plugin.Plugin
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.core.runtime.PnLibraryBootstrap
import ru.privatenull.pnlibrary.core.runtime.PnLibraryImpl
import ru.privatenull.pnlibrary.core.runtime.PnLibraryConfigLoader
import ru.privatenull.pnlibrary.core.updates.MandatoryUpdateService
import java.nio.file.Paths

class PnLibraryBungeePlugin : Plugin() {
    private var runtime: PnLibrary? = null
    private var updateMonitor: AutoCloseable? = null

    override fun onEnable() {
        val adapter = BungeePlatformAdapter(this)
        val loaded = PnLibraryBootstrap.bootstrap(this, adapter, PnLibraryConfigLoader.load(dataFolder.toPath()))
        adapter.attachLibrary(loaded as PnLibraryImpl)
        runtime = loaded
        val currentJar = Paths.get(javaClass.protectionDomain.codeSource.location.toURI())
        updateMonitor = MandatoryUpdateService.start(this, adapter, description.version, "bungee", currentJar,
            dataFolder.toPath().parent.resolve("update"))
        logger.info("pnLibrary enabled (bungeecord)")
    }

    override fun onDisable() {
        runCatching { updateMonitor?.close() }
        updateMonitor = null
        runtime?.close()
        runtime = null
    }
}
