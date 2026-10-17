package ru.privatenull.pnlibrary.api.updates

import java.time.Instant

/**
 * Minimal release history entry used by status views.
 *
 * @property version published version text
 * @property channel declared release maturity
 * @property publishedAt publisher-provided release time
 */
data class ReleaseSummary(
    val version: String,
    val channel: UpdateChannel,
    val publishedAt: Instant? = null,
    /** Link to the human-readable changelog for this release, when published. */
    val changelogUrl: String? = null,
)
