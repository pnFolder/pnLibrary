package org.pnfolder.pnlibrary.api.compat

/** What pnLibrary does when the server is newer than the newest Minecraft version a plugin or module declared. */
enum class NewerAction {
    /** Starts as usual, silently. */
    ALLOW,

    /** Starts and writes a warning that the version is newer than the checked ones; the default. */
    ALLOW_WITH_WARNING,

    /** Treats it as unsupported, see [UnsupportedAction]. */
    BLOCK,
}
