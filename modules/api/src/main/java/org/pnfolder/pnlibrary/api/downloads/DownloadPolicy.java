package org.pnfolder.pnlibrary.api.downloads;

/** Whether pnLibrary may download a file by itself, or only when an administrator asks. */
public enum DownloadPolicy {
    /** Only when an administrator asks ({@code /pn deps}, {@code /pn downloads now}, the menu). */
    MANUAL,

    /** By itself, while the setting {@code downloads.automatic} is on. */
    AUTOMATIC,

    /** By itself even when {@code downloads.automatic} is off. */
    FORCED
}
