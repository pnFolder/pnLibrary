package org.pnfolder.pnlibrary.api.compat.declaration;

/** What pnLibrary does when the server is newer than the newest Minecraft version a plugin or module declared. */
public enum NewerAction {
    /** Starts as usual, silently. */
    ALLOW,

    /** Starts and writes a warning that the version is newer than the checked ones; the default. */
    ALLOW_WITH_WARNING,

    /** Treats it as unsupported, see {@link UnsupportedAction}. */
    BLOCK
}
