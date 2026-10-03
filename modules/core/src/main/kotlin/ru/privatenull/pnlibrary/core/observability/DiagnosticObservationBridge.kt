package ru.privatenull.pnlibrary.core.observability

import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticLevel
import ru.privatenull.pnlibrary.api.observability.ObservationLevel
import ru.privatenull.pnlibrary.api.observability.ObservationRequest

/** Translates one legacy diagnostic incident into one modern observation. */
internal class DiagnosticObservationBridge(
    private val runtime: ObservabilityRuntime,
) {
    fun record(
        plugin: String,
        level: DiagnosticLevel,
        component: String,
        code: String,
        message: String,
        error: Throwable?,
        fields: Map<String, Any?>,
    ) {
        runtime.record(
            ObservationRequest(
                plugin = plugin,
                source = component,
                message = message,
                level = level.toObservationLevel(),
                data = observationData(code, error, fields),
                error = error,
            ),
        )
    }

    private fun observationData(
        code: String,
        error: Throwable?,
        fields: Map<String, Any?>,
    ): Map<String, String> = fields.toStringValues() + mapOf(
        "code" to code,
        "hasException" to (error != null).toString(),
    )
}

internal fun DiagnosticLevel.toObservationLevel(): ObservationLevel = when (this) {
    DiagnosticLevel.INFO -> ObservationLevel.INFO
    DiagnosticLevel.WARNING -> ObservationLevel.WARNING
    DiagnosticLevel.ERROR -> ObservationLevel.ERROR
}

internal fun Map<String, Any?>.toStringValues(): Map<String, String> =
    mapValues { (_, value) -> value?.toString() ?: "null" }
