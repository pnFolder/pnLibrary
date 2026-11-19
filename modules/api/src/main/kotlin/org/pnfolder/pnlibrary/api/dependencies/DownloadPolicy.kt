package org.pnfolder.pnlibrary.api.dependencies


/** Controls whether pnLibrary may stage a missing dependency. */
enum class DownloadPolicy {
    MANUAL,
    AUTOMATIC,
    FORCED
}
