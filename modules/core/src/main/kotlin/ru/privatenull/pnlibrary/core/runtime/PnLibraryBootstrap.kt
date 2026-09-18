package ru.privatenull.pnlibrary.core.runtime

import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.api.runtime.PnLibraryConfig
import ru.privatenull.pnlibrary.api.runtime.PnLibraryProvider
import ru.privatenull.pnlibrary.core.diagnostics.DiagnosticsRegistry
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter

/**
 * Creates and publishes the single pnLibrary runtime in the current JVM.
 *
 * This bootstrap owns initialization order only: create the implementation,
 * start internal services, install [PnLibraryProvider], then bind the
 * [PlatformAdapter]. Repeated calls while that runtime is active return the existing instance;
 * callers should therefore invoke bootstrap only from the native pnLibrary entry point.
 */
internal object PnLibraryBootstrap {

    private var instance: PnLibraryImpl? = null
    private val globalRegistry = DiagnosticsRegistry()

    @JvmStatic
    @JvmOverloads
    @Synchronized
    /**
     * Creates, initializes, publishes, and binds the process-wide runtime.
     *
     * Initialization order is deliberate: internal services start before [PnLibraryProvider] is
     * installed, and native commands/listeners bind only after provider installation. If any phase
     * fails, the partially initialized runtime and platform adapter are closed before the original
     * exception is rethrown.
     *
     * @param owner native plugin instance that owns the runtime
     * @param platform adapter for the current native platform
     * @param config already validated runtime configuration
     * @return the active runtime, which may be an instance created by an earlier call
     */
    fun bootstrap(
        owner: Any,
        platform: PlatformAdapter,
        config: PnLibraryConfig = PnLibraryConfig(),
    ): PnLibrary {
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
