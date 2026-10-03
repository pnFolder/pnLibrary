package ru.privatenull.pnlibrary.api.observability

import java.nio.file.Path

data class ObservabilityReportRequest(
    val target: String = "all",
    val includeLogs: Boolean = true,
    val includeConfigurations: Boolean = true,
)

data class ObservabilityReport(
    val id: String,
    val file: Path,
    val createdAt: Long,
)
