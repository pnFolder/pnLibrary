package ru.privatenull.pnlibrary.core.updates

import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.api.updates.UpdatePlanSnapshot
import ru.privatenull.pnlibrary.update.RefreshMode
import ru.privatenull.pnlibrary.spi.platform.PlatformExecution
import ru.privatenull.pnlibrary.spi.platform.PlatformLogging
import ru.privatenull.pnlibrary.update.ResolutionResult
import java.util.concurrent.Executors
import java.nio.file.Path

/** Owns the scheduler and lifecycle wiring around the update orchestrator. */
internal class UpdateOrchestratorRuntime(
    private val configuration: UpdateConfiguration,
    dataFolder: Path,
    private val logging: PlatformLogging,
    execution: PlatformExecution,
    installer: UpdateInstaller,
    registrations: () -> List<RegisteredUpdate>,
    resolveGraph: (RefreshMode) -> ResolutionResult,
    stageGraph: (UpdatePlanSnapshot) -> Unit,
    announce: (UpdatePlanSnapshot) -> Unit,
    automaticAllowed: (UpdatePlanSnapshot) -> Boolean,
) : AutoCloseable {
    private val executor = Executors.newSingleThreadScheduledExecutor { action ->
        Thread(action, "pnLibrary-update-orchestrator").apply { isDaemon = true }
    }

    val orchestrator = UpdateOrchestrator(
        configuration,
        UpdateStateStore(dataFolder.resolve("updates"), warning = { logging.log(this, LogLevel.WARNING, it) }),
        executor,
        { resolveGraph(RefreshMode.CACHED) },
        stageGraph,
        announce,
        automaticAllowed = automaticAllowed,
        remoteResolver = { resolveGraph(RefreshMode.FORCE_REMOTE) },
    ).also {
        runCatching { installer.recoverInterruptedTransactions() }.onFailure { error ->
            logging.log(this, LogLevel.WARNING, "Update recovery failed", error)
        }
        it.start()
        execution.whenServerReady(Runnable {
            executor.execute { installer.reconcilePending(registrations(), it::healthResolved) }
        })
    }

    override fun close() {
        orchestrator.close()
    }
}
