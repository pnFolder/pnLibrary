package org.pnfolder.pnlibrary.core.commands.tasks

import org.pnfolder.pnlibrary.api.tasks.TaskSnapshot
import java.time.Duration
import java.time.Instant

enum class TaskPeriod(val word: String, val label: String) {
    TODAY("today", "сегодня"),

    WEEK("week", "7 дней"),

    OLDER("older", "раньше"),

    ANY("any", "любое время"),
    ;

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
