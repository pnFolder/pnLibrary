package org.pnfolder.pnlibrary.core.commands

import org.pnfolder.pnlibrary.api.tasks.TaskSnapshot
import java.time.Duration
import java.time.Instant

/** Which tasks to show by date; tasks that are still active always count as current. */
enum class TaskPeriod(val word: String, val label: String) {
    /** Something happened today, in the configured time zone. */
    TODAY("today", "сегодня"),

    /** Something happened in the last seven days. */
    WEEK("week", "7 дней"),

    /** Finished before today. */
    OLDER("older", "раньше"),

    /** Any date. */
    ANY("any", "любое время"),
    ;

    /** Whether [task] belongs to this period. */
    fun keep(task: TaskSnapshot): Boolean = when (this) {
        TODAY -> TaskLabels.isToday(task) || TaskLabels.isActive(task.status)
        WEEK -> TaskLabels.activity(task).isAfter(Instant.now().minus(Duration.ofDays(WEEK_DAYS))) || TaskLabels.isActive(task.status)
        OLDER -> !TaskLabels.isToday(task) && !TaskLabels.isActive(task.status)
        ANY -> true
    }

    private companion object {
        const val WEEK_DAYS = 7L
    }
}
