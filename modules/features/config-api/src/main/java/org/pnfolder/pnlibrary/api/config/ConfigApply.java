package org.pnfolder.pnlibrary.api.config;

/** When an edited setting starts to work. */
public enum ConfigApply {
    /** At once: the plugin reads the value every time it needs it. */
    LIVE,

    /** After the file is read again: the plugin takes the value when it handles {@link ManagedConfig#onReloadRequired}. */
    RELOAD,

    /** After the server restarts. */
    RESTART
}
