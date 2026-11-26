package org.pnfolder.pnlibrary.api.downloads;

/** Lifecycle state reported for one managed file download. */
public enum DownloadState {
    DECLARED,
    CURRENT,
    DOWNLOADING,
    STAGED,
    BLOCKED,
    FAILED,
    CLOSED
}
