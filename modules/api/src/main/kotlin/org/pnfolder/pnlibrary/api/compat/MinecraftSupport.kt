package org.pnfolder.pnlibrary.api.compat

/** How the server's Minecraft version stands against what a plugin or module declared. */
enum class MinecraftSupport {
    /** Nothing was declared, so every version is fine. */
    NOT_DECLARED,

    /** The version is inside the declared versions. */
    SUPPORTED,

    /** Newer than the newest declared version, also a release pnLibrary does not know yet. */
    NEWER_THAN_DECLARED,

    /** Older than the oldest declared version. */
    OLDER_THAN_DECLARED,

    /** Between declared versions but not one of them, or listed as excluded. */
    EXCLUDED,

    /** The server's version cannot be read (a proxy, or an unusual build), so nothing is checked. */
    UNKNOWN,
}
