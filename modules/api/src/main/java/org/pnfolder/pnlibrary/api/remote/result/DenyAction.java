package org.pnfolder.pnlibrary.api.remote.result;

/** Action applied when a remote policy denies operation. */
public enum DenyAction {
    /** Disables the whole plugin. */
    DISABLE_PLUGIN,
    /** Disables only the module the policy guards. */
    DISABLE_MODULE
}
