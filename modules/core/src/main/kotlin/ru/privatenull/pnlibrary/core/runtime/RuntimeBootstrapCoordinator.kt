package ru.privatenull.pnlibrary.core.runtime

import ru.privatenull.pnlibrary.api.runtime.PnLibrary

/** Coordinates native bootstrap, shared runtime startup, and platform-facing startup metadata. */
internal object RuntimeBootstrapCoordinator {
    data class Started(
        val library: PnLibrary,
        val currentVersion: String,
        val updateMonitor: AutoCloseable,
        val platformName: String,
    )

    fun start(owner: Any, context: PlatformRuntimeContext): Started {
        val library = PnLibraryBootstrap.bootstrap(
            owner,
            context,
            PnLibraryConfigLoader.load(context.dataFolder),
        )
        return try {
            val runtime = RuntimeStartup.start(owner, library, context.identity, context.ownership)
            Started(runtime.library, runtime.currentVersion, runtime.updateMonitor, context.summaryName)
        } catch (error: Throwable) {
            library.close()
            throw error
        }
    }
}
