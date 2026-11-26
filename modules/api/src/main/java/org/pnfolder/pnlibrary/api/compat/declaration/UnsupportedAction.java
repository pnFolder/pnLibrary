package org.pnfolder.pnlibrary.api.compat.declaration;

/** What pnLibrary does when the server's Minecraft version is outside what a plugin or module declared. */
public enum UnsupportedAction {
    /**
     * The module is switched off with the reason; the rest of the plugin keeps working. A module without hooks cannot
     * be switched off, so registering it fails.
     */
    DISABLE_MODULE,

    /** Registering the module fails with the reason, so the plugin does not start. */
    STOP_PLUGIN,

    /** Only a warning in the log; everything starts. */
    WARN_ONLY
}
