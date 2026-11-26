package org.pnfolder.pnlibrary.api.config;

/** What to do with YAML keys that no longer exist in the typed model. */
public enum UnknownValuePolicy {
    /** Keep unknown keys when the file is synchronized. */
    PRESERVE,

    /** Delete unknown keys during synchronization. */
    REMOVE,

    /** Reject the file when at least one unknown key is present. */
    FAIL
}
