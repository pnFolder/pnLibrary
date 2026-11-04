package org.pnfolder.pnlibrary.core.commands.tasks

import org.pnfolder.pnlibrary.api.tasks.TaskSnapshot
import org.pnfolder.pnlibrary.api.tasks.TaskStatus

/** Which tasks to show by state. */
enum class TaskState(val word: String, val label: String) {
    /** Every state. */
    ALL("all", "все"),

    /** Waiting or running. */
    ACTIVE("active", "работают"),

    /** Failed, or failed at least once. */
    FAILED("failed", "с ошибками"),

    /** Completed or cancelled. */
    DONE("done", "закончены"),
    ;

    /** Whether [task] is in this state. */
    fun keep(task: TaskSnapshot): Boolean = when (this) {
        ALL -> true
        ACTIVE -> TaskLabels.isActive(task.status)
        FAILED -> task.status == TaskStatus.FAILED || task.lastFailure != null
        DONE -> task.status == TaskStatus.COMPLETED || task.status == TaskStatus.CANCELLED
    }
}
