package org.pnfolder.pnlibrary.api.config;

/** What to do when the configuration file does not exist. */
public enum MissingFilePolicy {
    /** Serialize the code-defined defaults to a new file, then load them. */
    CREATE,

    /** Abort loading instead of creating a file. */
    FAIL
}
