package org.pnfolder.pnlibrary.core.remote;

/** How the check made before the plugin started ended. */
public enum StartupCheck {
    PASSED,

    /** The policy could not be checked, and {@code KEEP_RUNNING} lets the plugin start anyway. */
    UNCHECKED
}
