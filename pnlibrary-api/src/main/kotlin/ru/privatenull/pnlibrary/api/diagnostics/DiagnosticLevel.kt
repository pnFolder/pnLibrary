package ru.privatenull.pnlibrary.api.diagnostics

/** Severity level for diagnostic events recorded via [DiagnosticsService]. */
enum class DiagnosticLevel {
    /** Informational state change or operation that completed normally. */
    INFO,

    /** Recoverable problem or degraded behavior that deserves attention. */
    WARNING,

    /** Failed operation or unavailable component. */
    ERROR,
}
