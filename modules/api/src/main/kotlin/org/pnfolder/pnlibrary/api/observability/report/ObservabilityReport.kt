package org.pnfolder.pnlibrary.api.observability.report

import java.nio.file.Path

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
