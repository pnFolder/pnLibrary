package org.pnfolder.pnlibrary.core.runtime.lifecycle

import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.core.runtime.details.RuntimeUpdateMonitor
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnership

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
