package org.pnfolder.pnlibrary.api.metrics.error

/**
 * Provider-neutral, normalized error event suitable for local or remote reporting.
 *
 * @property type fully qualified exception type or another stable error identifier
 * @property message human-readable error message, when one is available
 * @property stackTrace rendered stack frames ordered from the failure site outward
 * @property handled whether application code caught and handled the failure
 * @property operation optional logical operation that was executing when the failure occurred
 * @property attributes additional structured context associated with this occurrence
 */
data class TelemetryError(
    val type: String,
    val message: String?,
    val stackTrace: List<String>,
    val handled: Boolean,
    val operation: String? = null,
    val attributes: Map<String, Any?> = emptyMap(),
)
