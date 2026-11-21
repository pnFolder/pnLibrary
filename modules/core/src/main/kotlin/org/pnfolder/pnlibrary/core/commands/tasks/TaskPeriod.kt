package org.pnfolder.pnlibrary.core.commands.tasks

import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot
import java.time.Duration
import java.time.Instant

/** Age bucket of a finished task in the task list; [word] is the command argument. */
enum class TaskPeriod(val word: String, val label: String) {
    TODAY("today", "сегодня"),

    WEEK("week", "7 дней"),

    OLDER("older", "раньше"),

    ANY("any", "любое время"),
    ;

    fun keep(task: TaskSnapshot): Boolean = when (this) {
        TODAY -> TaskLabels.isToday(task) || task.isActive
        WEEK -> TaskLabels.activity(task).isAfter(Instant.now().minus(Duration.ofDays(WEEK_DAYS))) || task.isActive
        OLDER -> !TaskLabels.isToday(task) && !task.isActive
        ANY -> true
    }

    private companion object {
        const val WEEK_DAYS = 7L
    }
}
