package org.pnfolder.pnlibrary.api.downloads

/** Whether pnLibrary may download a file by itself, or only when an administrator asks. */
enum class DownloadPolicy {
    /** Only when an administrator asks (`/pn deps`, `/pn downloads now`, the menu). */
    MANUAL,

    /** By itself, while the setting `downloads.automatic` is on. */
    AUTOMATIC,

    /** By itself even when `downloads.automatic` is off. */
    FORCED,
}
