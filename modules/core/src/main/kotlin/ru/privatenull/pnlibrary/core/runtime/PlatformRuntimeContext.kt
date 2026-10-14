package ru.privatenull.pnlibrary.core.runtime

import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.spi.platform.PlatformDiagnostics
import ru.privatenull.pnlibrary.spi.platform.PlatformExecution
import ru.privatenull.pnlibrary.spi.platform.PlatformIdentity
import ru.privatenull.pnlibrary.spi.platform.PlatformLogging
import ru.privatenull.pnlibrary.spi.platform.PlatformLogObservation
import ru.privatenull.pnlibrary.spi.platform.PlatformOwnership
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import ru.privatenull.pnlibrary.spi.platform.PlatformMetrics
import ru.privatenull.pnlibrary.spi.platform.PlatformCommands
import ru.privatenull.pnlibrary.spi.platform.PlatformAudience
import ru.privatenull.pnlibrary.spi.platform.PlatformTasks
import ru.privatenull.pnlibrary.spi.platform.PlatformReadiness
import ru.privatenull.pnlibrary.spi.platform.PlatformBinding
import ru.privatenull.pnlibrary.spi.platform.PlatformShutdown
import java.nio.file.Path

/** Prepared platform capabilities passed from the native boundary into the shared runtime. */
internal data class PlatformRuntimeContext(
    val identity: PlatformIdentity,
    val execution: PlatformExecution,
    val readiness: PlatformReadiness,
    val ownership: PlatformOwnership,
    val diagnostics: PlatformDiagnostics,
    val logObservation: PlatformLogObservation,
    val logging: PlatformLogging,
    val metrics: PlatformMetrics,
    val commands: PlatformCommands,
    val audience: PlatformAudience,
    val tasks: PlatformTasks,
    val dataFolder: Path,
    val binding: PlatformBinding,
    val shutdown: PlatformShutdown,
) {
    val summaryName: String
        get() = if (identity.implementationName.equals(identity.type.displayName, ignoreCase = true)) {
            identity.type.displayName
        } else {
            "${identity.type.displayName} / ${identity.implementationName}"
        }
}

internal fun PlatformAdapter.toRuntimeContext(): PlatformRuntimeContext = PlatformRuntimeContext(
    identity = this,
    execution = this,
    readiness = this,
    ownership = this,
    diagnostics = this,
    logObservation = this,
    logging = this,
    metrics = this,
    commands = this,
    audience = this,
    tasks = this,
    dataFolder = dataFolder,
    binding = this,
    shutdown = this,
)
