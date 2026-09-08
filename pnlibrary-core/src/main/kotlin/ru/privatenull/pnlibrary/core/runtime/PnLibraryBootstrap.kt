package ru.privatenull.pnlibrary.core.runtime

import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.api.runtime.PnLibraryConfig
import ru.privatenull.pnlibrary.api.runtime.PnLibraryProvider
import ru.privatenull.pnlibrary.core.diagnostics.DiagnosticsRegistry

/**
 * Creates and publishes the single pnLibrary runtime in the current JVM.
 *
 * This bootstrap owns initialization order only: create the implementation,
 * start internal services, install [PnLibraryProvider], then bind the
 * [PlatformAdapter]. Repeated calls return the active instance.
 */
object PnLibraryBootstrap {

    private var instance: PnLibraryImpl? = null
    private val globalRegistry = DiagnosticsRegistry()

    @JvmStatic
    @JvmOverloads
    @Synchronized
    fun bootstrap(owner: Any, platform: PlatformAdapter, config: PnLibraryConfig = PnLibraryConfig()): PnLibrary {
        requireNotNull(owner) { "owner plugin must not be null" }
        requireNotNull(platform) { "platform adapter must not be null" }

        val current = instance
        if (current != null && !current.isClosed) return current
        val created = PnLibraryImpl(
            owner = owner,
            platform = platform,
            diagnostics = globalRegistry,
            config = config,
            onClose = { instance = null },
        )
        instance = created

        return try {
            created.init()
            PnLibraryProvider.install(created)
            platform.bind(created)
            created
        } catch (error: Throwable) {
            created.close()
            throw error
        }
    }

    /** Shared diagnostics registry of the active process. */
    @JvmStatic
    fun globalRegistry(): DiagnosticsRegistry = globalRegistry
}
