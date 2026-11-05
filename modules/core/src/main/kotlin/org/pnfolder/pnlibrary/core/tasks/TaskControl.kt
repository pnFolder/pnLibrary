package org.pnfolder.pnlibrary.core.tasks

/** What came of an administrator stopping or starting a task. */
enum class TaskControl {
    /** The task did what was asked. */
    DONE,

    /** The plugin that owns the task does not allow it. */
    FORBIDDEN,

    /** The task is not in a state where this is possible, or it is gone. */
    UNAVAILABLE,
}
