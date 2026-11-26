package org.pnfolder.pnlibrary.api.tasks.spec;

/** Policy applied when a scope already contains the requested task key. */
public enum TaskConflictPolicy {
    /** Fail the new schedule request. */
    REJECT,
    /** Keep the live task and return its handle. */
    KEEP_EXISTING,
    /** Cancel the live task and schedule the new one. */
    REPLACE
}
