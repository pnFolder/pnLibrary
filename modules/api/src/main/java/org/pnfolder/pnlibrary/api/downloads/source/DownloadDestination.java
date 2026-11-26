package org.pnfolder.pnlibrary.api.downloads.source;

/** Root used to resolve a downloaded file's relative destination path. */
public enum DownloadDestination {
    /** The data folder of the owning plugin. */
    DATA_FOLDER,

    /** The library's cache folder. */
    CACHE
}
