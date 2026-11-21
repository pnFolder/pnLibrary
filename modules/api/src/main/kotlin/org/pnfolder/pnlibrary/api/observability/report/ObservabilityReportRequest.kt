package org.pnfolder.pnlibrary.api.observability.report

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
