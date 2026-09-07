package ru.privatenull.pnlibrary.api.diagnostics

import java.nio.file.Path

/**
 * Result of building a diagnostic report.
 *
 * A local file is always created. [uploadedUrl] is present only after a
 * successful upload; [uploadError] reports upload failure without losing the
 * local report.
 */
data class DiagnosticReport(
    /** Path to the saved local report. */
    val localFile: Path,
    /** Whether the file payload is encrypted. */
    val encrypted: Boolean,
    /** Public URL of the uploaded report, or `null`. */
    val uploadedUrl: String?,
    /** Upload error description, or `null`. */
    val uploadError: String?,
)
