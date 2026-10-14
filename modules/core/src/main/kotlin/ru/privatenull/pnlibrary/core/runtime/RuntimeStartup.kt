package ru.privatenull.pnlibrary.core.runtime

import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.spi.platform.PlatformIdentity
import ru.privatenull.pnlibrary.spi.platform.PlatformOwnership

/** Attaches the mandatory self-update monitor to an already bootstrapped runtime. */
internal object RuntimeStartup {
    data class Started(
        val library: PnLibrary,
        val currentVersion: String,
        val updateMonitor: AutoCloseable,
    )

    fun start(
        owner: Any,
        library: PnLibrary,
        identity: PlatformIdentity,
        ownership: PlatformOwnership,
    ): Started {
        return try {
            val update = RuntimeUpdateMonitor.register(
                library = library,
                owner = owner,
                identity = identity,
                ownership = ownership,
            )
            Started(library, update.currentVersion, update.monitor)
        } catch (error: Throwable) {
            runCatching { library.close() }.onFailure(error::addSuppressed)
            throw error
        }
    }
}
