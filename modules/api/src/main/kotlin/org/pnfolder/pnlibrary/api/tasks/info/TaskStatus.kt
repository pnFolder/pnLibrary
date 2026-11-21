package org.pnfolder.pnlibrary.api.tasks.info

/** Current lifecycle state of a scheduled task. */
enum class TaskStatus {
    /** Waiting for its first or next invocation. */
    SCHEDULED,

    /** An invocation is running now. */
    RUNNING,

    /** A one-shot task ran to its end. */
    COMPLETED,

    /** The owner or an administrator stopped the task. */
    CANCELLED,

    /** The task stopped because an invocation failed. */
    FAILED,
    ;

    /** Whether the task can no longer run: [COMPLETED], [CANCELLED] or [FAILED]. */
    val isTerminal: Boolean get() = this == COMPLETED || this == CANCELLED || this == FAILED
}
