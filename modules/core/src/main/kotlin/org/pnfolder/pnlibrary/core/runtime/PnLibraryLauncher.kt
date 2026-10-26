package org.pnfolder.pnlibrary.core.runtime

import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig
import org.pnfolder.pnlibrary.api.runtime.PnLibraryProvider
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import org.pnfolder.pnlibrary.core.commands.ProxyControlCommand
import org.pnfolder.pnlibrary.core.diagnostics.DiagnosticsRegistry
import org.pnfolder.pnlibrary.core.runtime.platform.PlatformRuntimeContext

/**
 * Creates and publishes the single pnLibrary runtime in the current JVM.
 *
 * This bootstrap owns initialization order only: create the implementation,
 * start internal services, install [PnLibraryProvider], then bind the prepared platform context.
 * Repeated calls while that runtime is active return the existing instance;
 * callers should therefore invoke bootstrap only from the native pnLibrary entry point.
 */
internal object PnLibraryLauncher {

    private var instance: PnLibraryImpl? = null
    private val globalRegistry = DiagnosticsRegistry()

    /**
     * Creates, initializes, publishes, and binds the process-wide runtime.
     *
     * Initialization order is deliberate: internal services start before [PnLibraryProvider] is
     * installed, and native commands/listeners bind only after provider installation. If any phase
     * fails, the partially initialized runtime and platform resources are closed before the original
     * exception is rethrown.
     *
     * @param owner native plugin instance that owns the runtime
     * @param platform prepared platform capabilities from the native boundary
     * @param config already validated runtime configuration
     * @return the active runtime, which may be an instance created by an earlier call
     */
    @JvmStatic
    @JvmOverloads
    @Synchronized
    fun bootstrap(
        owner: Any,
        platform: PlatformRuntimeContext,
        config: PnLibraryConfig = PnLibraryConfig(),
    ): PnLibrary {
        requireNotNull(owner) { "Owner plugin must not be null" }
        requireNotNull(platform) { "Platform context must not be null" }

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
            platform.binding.bind(created)
            if (platform.identity.isProxy) {
                ProxyControlCommand(created).register()
            }
            created
        } catch (error: Throwable) {
            runCatchingNonFatal { created.close() }.onFailure(error::addSuppressed)
            throw error
        }
    }

    /** Shared diagnostics registry of the active process. */
    @JvmStatic
    fun globalRegistry(): DiagnosticsRegistry = globalRegistry
}
