package org.pnfolder.pnlibrary.api.runtime.group;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.diagnostics.DebugRequest;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticsService;
import org.pnfolder.pnlibrary.api.diagnostics.report.DiagnosticReport;
import org.pnfolder.pnlibrary.api.logging.LoggingService;
import org.pnfolder.pnlibrary.api.metrics.MetricsService;
import org.pnfolder.pnlibrary.api.observability.ObservabilityService;

/**
 * Everything that shows what the server is doing: logs, metrics, diagnostics and support reports.
 *
 * <pre>{@code
 * LoggingService logging = PnLibraryProvider.get().getObserve().getLogging();
 * }</pre>
 */
@ApiStatus.NonExtendable
public interface Monitoring {
    /** Native logging and formatted lifecycle summaries. */
    @NotNull
    LoggingService getLogging();

    /** Managed bStats sessions; every plugin supplies its own project ID. */
    @NotNull
    MetricsService getMetrics();

    /** Registry for diagnostic data contributed by consumer plugins. */
    @NotNull
    DiagnosticsService getDiagnostics();

    /** Observations, component status and support reports. */
    @NotNull
    ObservabilityService getObservability();

    /** Builds a diagnostic report from an already validated request. */
    @NotNull
    DiagnosticReport report(@NotNull DebugRequest request);
}
