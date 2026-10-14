package ru.privatenull.pnlibrary.core.runtime

import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import java.nio.file.Path

/** Creates the shared runtime and attaches its mandatory self-update monitor. */
internal object RuntimeStartup {
    data class Started(
        val library: PnLibrary,
        val currentVersion: String,
        val updateMonitor: AutoCloseable,
    )

    fun start(owner: Any, platform: PlatformAdapter, updateDirectory: Path): Started {
        requireNotNull(updateDirectory) { "update directory must not be null" }
        val dataFolder = requireNotNull(platform.dataFolder) {
            "Platform adapter must provide the pnLibrary data folder"
        }
        val library = PnLibraryBootstrap.bootstrap(
            owner,
            platform,
            PnLibraryConfigLoader.load(dataFolder),
        )
        return try {
            val update = RuntimeUpdateMonitor.register(
                library = library,
                owner = owner,
                identity = platform,
                ownership = platform,
            )
            Started(library, update.currentVersion, update.monitor)
        } catch (error: Throwable) {
            library.close()
            throw error
        }
    }
}
