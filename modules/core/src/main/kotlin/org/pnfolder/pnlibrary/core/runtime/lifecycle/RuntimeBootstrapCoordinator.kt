package org.pnfolder.pnlibrary.core.runtime.lifecycle

import org.pnfolder.pnlibrary.api.logging.LogLevel
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.core.runtime.PnLibraryConfigLoader
import org.pnfolder.pnlibrary.core.runtime.PnLibraryLauncher
import org.pnfolder.pnlibrary.core.runtime.platform.PlatformRuntimeContext

/** Coordinates native bootstrap, shared runtime startup, and platform-facing startup metadata. */
internal object RuntimeBootstrapCoordinator {
    data class Started(
        val library: PnLibrary,
        val currentVersion: String,
        val updateMonitor: AutoCloseable,
        val platformName: String,
    )

    fun start(owner: Any, context: PlatformRuntimeContext): Started {
        val library = PnLibraryLauncher.bootstrap(
            owner,
            context,
            PnLibraryConfigLoader.load(context.dataFolder) { context.logging.log(owner, LogLevel.WARNING, it) },
        )
        return try {
            val runtime = RuntimeStartup.start(owner, library, context.identity, context.ownership)
            Started(runtime.library, runtime.currentVersion, runtime.updateMonitor, context.summaryName)
        } catch (error: Throwable) {
            runCatching { library.close() }.onFailure(error::addSuppressed)
            throw error
        }
    }
}
