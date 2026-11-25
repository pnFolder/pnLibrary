package org.pnfolder.pnlibrary.core.downloads

import org.pnfolder.pnlibrary.api.downloads.source.DownloadDestination
import org.pnfolder.pnlibrary.core.settings.LibrarySettings

/** Administrator policy for downloads: whether they run, start automatically, and which destinations are allowed. */
internal data class DownloadConfiguration(
    val enabled: Boolean = true,
    val automatic: Boolean = true,
    val destinations: Set<DownloadDestination> = setOf(
        DownloadDestination.DATA_FOLDER, DownloadDestination.CACHE,
    ),
)

/** The download policy in these settings (the `downloads.*` keys). */
internal fun LibrarySettings.downloads(): DownloadConfiguration = DownloadConfiguration(
    enabled = downloadsEnabled,
    automatic = downloadsAutomatic,
    destinations = buildSet {
        if (downloadsToDataFolder) add(DownloadDestination.DATA_FOLDER)
        if (downloadsToCache) add(DownloadDestination.CACHE)
    },
)
