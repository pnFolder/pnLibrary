package org.pnfolder.pnlibrary.api.downloads;

/** Whether pnLibrary may download a file by itself, or only when an administrator asks. */
public enum DownloadPolicy {
    /** Only when an administrator asks ({@code /pn deps}, {@code /pn downloads now}, the menu). */
    MANUAL,

    /** By itself, as soon as the plugin registers the file. */
    AUTOMATIC,

    /** The same as {@link #AUTOMATIC}: downloads always start by themselves now; kept for plugins that name it. */
    FORCED
}
