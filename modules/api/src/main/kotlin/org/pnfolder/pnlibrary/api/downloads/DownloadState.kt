package org.pnfolder.pnlibrary.api.downloads


/** Lifecycle state reported for one managed file download. */
enum class DownloadState {
    DECLARED,
    CURRENT,
    DOWNLOADING,
    STAGED,
    BLOCKED,
    FAILED,
    CLOSED
}
