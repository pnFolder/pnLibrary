package org.pnfolder.pnlibrary.core.commands.tasks

import org.pnfolder.pnlibrary.api.tasks.TaskSnapshot
import org.pnfolder.pnlibrary.api.tasks.TaskStatus

enum class TaskState(val word: String, val label: String) {
    ALL("all", "все"),

    ACTIVE("active", "работают"),

    FAILED("failed", "с ошибками"),

    DONE("done", "закончены"),
    ;

    fun keep(task: TaskSnapshot): Boolean = when (this) {
        ALL -> true
        ACTIVE -> TaskLabels.isActive(task.status)
        FAILED -> task.status == TaskStatus.FAILED || task.lastFailure != null
        DONE -> task.status == TaskStatus.COMPLETED || task.status == TaskStatus.CANCELLED
    }
}
