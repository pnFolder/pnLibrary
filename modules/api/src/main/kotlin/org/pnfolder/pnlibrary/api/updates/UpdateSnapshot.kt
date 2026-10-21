package org.pnfolder.pnlibrary.api.updates

import org.pnfolder.pnlibrary.api.updates.release.ReleaseSummary
import org.pnfolder.pnlibrary.api.version.ApiVersionRange

/**
 * Immutable point-in-time state of one registered product updater.
 *
 * @property product display name resolved from native plugin metadata
 * @property currentVersion version currently running
 * @property latestVersion newest compatible release version, when known
 * @property channel configured release maturity
 * @property state current updater phase
 * @property currentJava Java feature version running the server
 * @property requiredJava minimum Java version of the selected artifact
 * @property automaticDownload whether checks may download a verified artifact automatically
 * @property releaseUrl public release page, when one is known
 * @property message failure or status detail intended for diagnostics, when present
 */
class UpdateSnapshot(
    val product: String,
    val currentVersion: String,
    val latestVersion: String?,
    val channel: UpdateChannel,
    val state: UpdateState,
    val currentJava: Int,
    val requiredJava: Int,
    val automaticDownload: Boolean,
    val releaseUrl: String?,
    val message: String?,
    /** Link to the human-readable changelog for [latestVersion], when published. */
    val latestChangelogUrl: String? = null,
    /** pnLibrary API generations supported by this registration, when declared. */
    val supportedApi: ApiVersionRange? = null,
    availableReleases: List<ReleaseSummary> = emptyList(),
) {
    /** Immutable copy of known catalog releases, including channels not selected for installation. */
    val availableReleases: List<ReleaseSummary> = availableReleases.frozen()
}
