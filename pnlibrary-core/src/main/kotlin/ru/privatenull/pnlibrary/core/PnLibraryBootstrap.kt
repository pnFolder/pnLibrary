package ru.privatenull.pnlibrary.core

import ru.privatenull.pnlibrary.api.PlatformAdapter
import ru.privatenull.pnlibrary.api.PnLibrary
import ru.privatenull.pnlibrary.api.PnLibraryConfig
import ru.privatenull.pnlibrary.api.PnLibraryProvider

/**
 * Internal lifecycle bootstrap for the one platform runtime in this process.
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
        return PnLibraryImpl(
                owner = owner,
                platform = platform,
                diagnostics = globalRegistry,
                config = config,
                onClose = { instance = null }
            ).also { impl ->
            instance = impl
            impl.init()
            PnLibraryProvider.install(impl)
        }
    }

    /** Returns the shared global diagnostics registry instance. */
    @JvmStatic
    fun globalRegistry(): DiagnosticsRegistry = globalRegistry
}
