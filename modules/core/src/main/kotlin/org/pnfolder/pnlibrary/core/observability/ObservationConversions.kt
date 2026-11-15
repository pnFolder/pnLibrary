package org.pnfolder.pnlibrary.core.observability

import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticLevel
import org.pnfolder.pnlibrary.api.observability.ObservationLevel

internal fun DiagnosticLevel.toObservationLevel(): ObservationLevel = when (this) {
    DiagnosticLevel.INFO -> ObservationLevel.INFO
    DiagnosticLevel.WARNING -> ObservationLevel.WARNING
    DiagnosticLevel.ERROR -> ObservationLevel.ERROR
}

internal fun Map<String, Any?>.toStringValues(): Map<String, String> =
    mapValues { (_, value) -> value?.toString() ?: "null" }
