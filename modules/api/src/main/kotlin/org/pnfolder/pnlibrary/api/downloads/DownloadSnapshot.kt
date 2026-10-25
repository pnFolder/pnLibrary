package org.pnfolder.pnlibrary.api.downloads

/**
 * Immutable status of one declared download.
 *
 * @property key application-defined download identifier
 * @property state current lifecycle state
 * @property message optional diagnostic or blocking explanation
 */
data class DownloadSnapshot(
    val key: String,
    val state: DownloadState,
    val message: String? = null
)
