package ru.privatenull.pnlibrary.core.runtime

import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.spi.platform.PlatformDiagnostics
import ru.privatenull.pnlibrary.spi.platform.PlatformExecution
import ru.privatenull.pnlibrary.spi.platform.PlatformIdentity
import ru.privatenull.pnlibrary.spi.platform.PlatformLogging
import ru.privatenull.pnlibrary.spi.platform.PlatformOwnership
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import ru.privatenull.pnlibrary.spi.platform.PlatformServices
import java.nio.file.Path

/** Prepared platform capabilities passed from the native boundary into the shared runtime. */
internal data class PlatformRuntimeContext(
    val identity: PlatformIdentity,
    val execution: PlatformExecution,
    val ownership: PlatformOwnership,
    val diagnostics: PlatformDiagnostics,
    val logging: PlatformLogging,
    val services: PlatformServices,
    val id: String,
    val implementationName: String,
    val isProxy: Boolean,
    val isServer: Boolean,
    val dataFolder: Path?,
    val bind: (PnLibrary) -> Unit,
    val close: () -> Unit,
) {
    val summaryName: String
        get() = if (implementationName.equals(identity.type.displayName, ignoreCase = true)) {
            identity.type.displayName
        } else {
            "${identity.type.displayName} / $implementationName"
        }
}

internal fun PlatformAdapter.toRuntimeContext(): PlatformRuntimeContext = PlatformRuntimeContext(
    identity = this,
    execution = this,
    ownership = this,
    diagnostics = this,
    logging = this,
    services = this,
    id = id,
    implementationName = implementationName,
    isProxy = isProxy,
    isServer = isServer,
    dataFolder = dataFolder,
    bind = this::bind,
    close = this::close,
)
