package org.pnfolder.pnlibrary.api.runtime.group

import org.jetbrains.annotations.ApiStatus

import org.pnfolder.pnlibrary.api.diagnostics.DebugRequest
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticsService
import org.pnfolder.pnlibrary.api.diagnostics.report.DiagnosticReport
import org.pnfolder.pnlibrary.api.logging.LoggingService
import org.pnfolder.pnlibrary.api.metrics.MetricsService
import org.pnfolder.pnlibrary.api.observability.ObservabilityService

/**
 * Everything that shows what the server is doing: logs, metrics, diagnostics and support reports.
 *
 * ```kotlin
 * val library = PnLibraryProvider.get()
 * library.observe.logging
 * ```
 */
@ApiStatus.NonExtendable
interface Monitoring {
    /** Native logging and formatted lifecycle summaries. */
    val logging: LoggingService

    /** Managed bStats sessions; every plugin supplies its own project ID. */
    val metrics: MetricsService

    /** Registry for diagnostic data contributed by consumer plugins. */
    val diagnostics: DiagnosticsService

    /** Observations, component status and support reports. */
    val observability: ObservabilityService

    /** Builds a diagnostic report from an already validated request. */
    fun report(request: DebugRequest): DiagnosticReport
}
