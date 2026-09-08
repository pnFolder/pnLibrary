package ru.privatenull.pnlibrary.api.runtime

import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticsService
import ru.privatenull.pnlibrary.api.diagnostics.DebugRequest
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticReport
import ru.privatenull.pnlibrary.api.events.EventService
import ru.privatenull.pnlibrary.api.logging.LoggingService
import ru.privatenull.pnlibrary.api.metrics.MetricsService
import ru.privatenull.pnlibrary.api.platform.PlatformAdapter
import ru.privatenull.pnlibrary.api.plugin.PluginRegistry
import ru.privatenull.pnlibrary.api.tasks.TaskService
import ru.privatenull.pnlibrary.api.updates.UpdateService
import java.io.Closeable

/**
 * Main pnLibrary facade shared by all plugins in one server process.
 *
 * Consumers obtain this interface through [PnLibraryProvider]. They must not
 * depend on `core` implementation classes, so the internals can evolve without
 * breaking installed plugins.
 */
interface PnLibrary : Closeable {
    /** Version of the installed runtime. */
    val version: String

    /** Returns `true` when this runtime is not older than [minimumVersion]. */
    fun isAtLeastVersion(minimumVersion: String): Boolean

    /** Native platform plugin instance that owns this runtime. */
    val owner: Any

    /** Bridge from the platform-independent core to the current server. */
    val platform: PlatformAdapter

    /** Validated configuration of the installed runtime. */
    val configuration: PnLibraryConfig

    /** Registry for diagnostic data contributed by consumer plugins. */
    val diagnostics: DiagnosticsService

    /** Managed bStats sessions; every plugin supplies its own project ID. */
    val metrics: MetricsService

    /** Native logging and formatted lifecycle summaries. */
    val logging: LoggingService

    /** Update checks and staged downloads for registered plugins. */
    val updates: UpdateService

    /** Cross-platform tasks grouped by their owner lifecycle. */
    val tasks: TaskService

    /** Synchronous cross-platform event bus with plugin-ID-bound subscriptions. */
    val events: EventService

    /** Global registry and high-level entry point for consumer plugins. */
    val plugins: PluginRegistry

    /** Builds a diagnostic report from an already validated request. */
    fun createDiagnosticReport(request: DebugRequest): DiagnosticReport

    /** Whether the runtime has released its resources. */
    val isClosed: Boolean

    /** Stops every service and removes this runtime from [PnLibraryProvider]. */
    override fun close()
}
