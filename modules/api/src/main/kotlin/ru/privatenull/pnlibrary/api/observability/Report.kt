package ru.privatenull.pnlibrary.api.observability

import java.nio.file.Path

/**
 * Controls which support data is included in an observability report.
 *
 * @property target plugin name to collect, or `all` for every registered plugin
 * @property includeLogs whether the bounded runtime log buffer is included
 * @property includeConfigurations whether registered sanitized configurations are included
 */
data class ObservabilityReportRequest(
    val target: String = "all",
    val includeLogs: Boolean = true,
    val includeConfigurations: Boolean = true,
)

/**
 * Identifies a support report stored on the local filesystem.
 *
 * @property id unique report identifier
 * @property file path to the generated report archive
 * @property createdAt creation time expressed as Unix epoch milliseconds
 */
data class ObservabilityReport(
    val id: String,
    val file: Path,
    val createdAt: Long,
)
