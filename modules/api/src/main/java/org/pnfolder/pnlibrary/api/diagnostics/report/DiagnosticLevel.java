package org.pnfolder.pnlibrary.api.diagnostics.report;

/** Severity level for diagnostic events recorded via {@code DiagnosticsService}. */
public enum DiagnosticLevel {
    /** Informational state change or operation that completed normally. */
    INFO,
    /** Recoverable problem or degraded behavior that deserves attention. */
    WARNING,
    /** Failed operation or unavailable component. */
    ERROR
}
