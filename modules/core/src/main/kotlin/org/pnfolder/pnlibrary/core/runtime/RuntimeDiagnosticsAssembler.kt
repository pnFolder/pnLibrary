package org.pnfolder.pnlibrary.core.runtime

import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig
import org.pnfolder.pnlibrary.api.updates.UpdateService
import org.pnfolder.pnlibrary.core.commands.CommandServiceImpl
import org.pnfolder.pnlibrary.core.diagnostics.DiagnosticsRegistry
import org.pnfolder.pnlibrary.core.metrics.MetricsServiceImpl
import org.pnfolder.pnlibrary.core.observability.SupportRuntime
import org.pnfolder.pnlibrary.core.tasks.TaskService
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity
import java.nio.file.Path

/** Assembles the runtime section of diagnostic reports from the composed runtime services. */
internal class RuntimeDiagnosticsAssembler(
    private val version: String,
    private val isClosed: () -> Boolean,
    private val identity: PlatformIdentity,
    private val dataFolder: Path,
    private val config: PnLibraryConfig,
    private val tasks: TaskService,
    private val updates: UpdateService,
    private val diagnostics: DiagnosticsRegistry,
    private val support: SupportRuntime,
    private val metrics: MetricsServiceImpl,
    private val commands: CommandServiceImpl,
    private val journal: () -> Map<String, Any?> = { emptyMap() },
) {
    /** Returns one ordered snapshot of library, task, update, metric, and support state. */
    fun assemble(): Map<String, Any?> {
        val taskSnapshots = tasks.query()
        val updateSnapshots = updates.all()
        val taskFailures = taskSnapshots.count { it.lastFailure != null }
        val failedUpdates = updateSnapshots.count { it.snapshot.state.name.equals("FAILED", ignoreCase = true) }
        val diagnosticSummary = diagnostics.diagnosticSummary()
        val history = diagnostics.historyAnalytics()
        val nativeLogs = support.logSummary()
        val reports = support.reportSummary()
        val observability = support.observabilityAnalytics()
        return linkedMapOf(
            "library" to linkedMapOf<String, Any?>(
                "version" to version,
                "closed" to isClosed(),
                "platform" to identity.id,
                "implementation" to identity.implementationName,
                "proxy" to identity.isProxy,
                "server" to identity.isServer,
                "dataFolder" to if (config.privacy) "[redacted]" else
                    dataFolder.toAbsolutePath().normalize().toString(),
                "configuration" to RuntimeConfigurationDetails.of(config),
            ),
            "journal" to runCatching(journal).getOrElse { linkedMapOf("error" to (it.message ?: it.javaClass.simpleName)) },
            "tasks" to RuntimeTaskDetails.entries(taskSnapshots),
            "taskSummary" to RuntimeTaskDetails.summary(taskSnapshots),
            "updates" to updateSnapshots.map(RuntimeUpdateDetails::registration),
            "updateSummary" to RuntimeUpdateSummary.of(updateSnapshots),
            "analytics" to RuntimeAnalytics.of(
                taskCount = taskSnapshots.size,
                updateCount = updateSnapshots.size,
                taskFailures = taskFailures,
                failedUpdates = failedUpdates,
                diagnosticSummary = diagnosticSummary,
                history = history,
                nativeLogs = nativeLogs,
                reports = reports,
                observability = observability,
            ),
            "metrics" to metrics.diagnosticSnapshot(),
            "metricsAnalytics" to metrics.diagnosticAnalytics(),
            "commandsAnalytics" to commands.diagnosticSnapshot(),
            "diagnostics" to diagnostics.diagnosticSummary(),
            "diagnosticHistory" to diagnostics.historyAnalytics(),
            "nativeLogs" to nativeLogs,
            "reports" to reports,
            "observability" to observability,
            "configurationDeclarations" to diagnostics.configurations("all").map { declaration ->
                linkedMapOf<String, Any?>(
                    "plugin" to declaration.plugin,
                    "path" to declaration.configuration.path,
                    "dataDirectory" to if (config.privacy) "[redacted]"
                    else declaration.dataDirectory?.toString(),
                )
            },
            "updateGraph" to linkedMapOf<String, Any?>(
                "current" to updates.currentPlan().orElse(null)?.let(RuntimeUpdateDetails::of),
                "history" to updates.history().map(RuntimeUpdateDetails::of),
            ),
        )
    }
}
