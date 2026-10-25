package org.pnfolder.pnlibrary.api.activity

/** Broad subsystem classification assigned to an activity event. */
enum class ActivityCategory {
    LIFECYCLE,
    USER_ACTION,
    UPDATE,
    DIAGNOSTICS,
    COMMAND,
    FEATURE,
    SECURITY,
    ERROR,
    SYSTEM
}
