package org.pnfolder.pnlibrary.core.runtime

import org.pnfolder.pnlibrary.spi.audiences.PlatformAudienceAdapter
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandAdapter
import org.pnfolder.pnlibrary.spi.metrics.PlatformMetricsFactory
import org.pnfolder.pnlibrary.spi.platform.PlatformAdapter
import org.pnfolder.pnlibrary.spi.platform.PlatformBinding
import org.pnfolder.pnlibrary.spi.platform.PlatformDiagnostics
import org.pnfolder.pnlibrary.spi.platform.PlatformExecution
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity
import org.pnfolder.pnlibrary.spi.platform.PlatformLogObservation
import org.pnfolder.pnlibrary.spi.platform.PlatformLogging
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnership
import org.pnfolder.pnlibrary.spi.platform.PlatformReadiness
import org.pnfolder.pnlibrary.spi.platform.PlatformShutdown
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskAdapter
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
    val metricsFactory: PlatformMetricsFactory,
    val commandAdapter: PlatformCommandAdapter,
    val audienceAdapter: PlatformAudienceAdapter,
    val taskAdapter: PlatformTaskAdapter,
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
    metricsFactory = metricsFactory,
    commandAdapter = commandAdapter,
    audienceAdapter = audienceAdapter,
    taskAdapter = taskAdapter,
    dataFolder = dataFolder,
    binding = this,
    shutdown = this,
)
