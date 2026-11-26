package org.pnfolder.pnlibrary.api.tasks.info;

/** Current lifecycle state of a scheduled task. */
public enum TaskStatus {
    /** Waiting for its first or next invocation. */
    SCHEDULED,
    /** An invocation is running now. */
    RUNNING,
    /** A one-shot task ran to its end. */
    COMPLETED,
    /** The owner or an administrator stopped the task. */
    CANCELLED,
    /** The task stopped because an invocation failed. */
    FAILED;

    /** Whether the task can no longer run: {@link #COMPLETED}, {@link #CANCELLED} or {@link #FAILED}. */
    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == FAILED;
    }
}
