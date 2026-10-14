package ru.privatenull.pnlibrary.core.runtime

import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter

/** Coordinates native bootstrap, shared runtime startup, and platform-facing startup metadata. */
internal object RuntimeBootstrapCoordinator {
    data class Started(
        val library: PnLibrary,
        val currentVersion: String,
        val updateMonitor: AutoCloseable,
        val platformName: String,
    )

    fun start(owner: Any, platform: PlatformAdapter): Started {
        val dataFolder = requireNotNull(platform.dataFolder) {
            "Platform adapter must provide the pnLibrary data folder"
        }
        val library = PnLibraryBootstrap.bootstrap(
            owner,
            platform,
            PnLibraryConfigLoader.load(dataFolder),
        )
        return try {
            val runtime = RuntimeStartup.start(owner, library, platform, platform)
            Started(runtime.library, runtime.currentVersion, runtime.updateMonitor, platform.summaryName())
        } catch (error: Throwable) {
            library.close()
            throw error
        }
    }
}

private fun PlatformAdapter.summaryName(): String =
    if (implementationName.equals(type.displayName, ignoreCase = true)) type.displayName
    else "${type.displayName} / $implementationName"
