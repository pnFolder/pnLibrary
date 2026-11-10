package org.pnfolder.pnlibrary.core.commands.tasks

import net.kyori.adventure.text.format.TextColor
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.tasks.TaskSnapshot
import org.pnfolder.pnlibrary.api.tasks.TaskStatus
import org.pnfolder.pnlibrary.common.time.DisplayZone
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Chat wording, colours and time formats for task statuses and moments. */
object TaskLabels {
    fun label(status: TaskStatus): String = when (status) {
        TaskStatus.SCHEDULED -> "ожидает запуска"
        TaskStatus.RUNNING -> "выполняется"
        TaskStatus.COMPLETED -> "завершена"
        TaskStatus.CANCELLED -> "отменена"
        TaskStatus.FAILED -> "упала"
    }

    fun color(status: TaskStatus): TextColor = when (status) {
        TaskStatus.SCHEDULED -> ChatPalette.INFO
        TaskStatus.RUNNING -> ChatPalette.OK
        TaskStatus.COMPLETED -> ChatPalette.SOFT
        TaskStatus.CANCELLED -> ChatPalette.FAINT
        TaskStatus.FAILED -> ChatPalette.DANGER
    }

    /** Whether the task can still run, so it can be cancelled. */
    fun isActive(status: TaskStatus): Boolean = status == TaskStatus.SCHEDULED || status == TaskStatus.RUNNING

    fun activity(task: TaskSnapshot): Instant = listOfNotNull(task.lastStartedAt, task.lastCompletedAt, task.createdAt).max()

    fun isToday(task: TaskSnapshot): Boolean =
        activity(task).atZone(DisplayZone.zone).toLocalDate() == LocalDate.now(DisplayZone.zone)

    fun title(task: TaskSnapshot): String = task.name ?: task.key ?: task.id.value.take(SHORT_ID)

    fun stopAnswer(task: TaskSnapshot): String = when {
        !isActive(task.status) -> "нет, задача уже ${label(task.status)}"
        !task.cancellable -> "нет, плагин запретил"
        else -> "да"
    }

    fun runAnswer(task: TaskSnapshot): String = when {
        !task.runnableNow -> "нет, плагин запретил"
        task.status == TaskStatus.RUNNING -> "нет, задача выполняется"
        task.status != TaskStatus.SCHEDULED -> "нет, задача уже ${label(task.status)}"
        else -> "да"
    }

    fun where(task: TaskSnapshot): String = when (task.executionKind.name) {
        "ASYNC" -> "отдельный поток, мир не трогать"
        "ENTITY" -> "поток существа"
        "REGION" -> "поток региона"
        else -> "основной поток сервера"
    }

    /** A moment in the configured time zone: `29 октября, 14:05:09`, with the year when it is not this year. */
    fun moment(at: Instant): String {
        val zone = DisplayZone.zone
        val year = if (at.atZone(zone).year == LocalDate.now(zone).year) "" else " yyyy"
        return DateTimeFormatter.ofPattern("d MMMM$year, HH:mm:ss", Locale("ru")).withZone(zone).format(at)
    }

    /** How long ago [at] was, or how long until it: `только что`, `5 мин назад`, `через 2 ч`. */
    fun ago(at: Instant): String {
        val gap = Duration.between(at, Instant.now())
        return when {
            gap.abs().seconds < SECONDS_PER_MINUTE -> "только что"
            gap.isNegative -> "через ${span(gap.negated())}"
            else -> "${span(gap)} назад"
        }
    }

    fun span(value: Duration): String {
        val millis = value.toMillis()
        return when {
            millis < MILLIS_PER_SECOND -> "$millis мс"
            millis < MILLIS_PER_SECOND * SECONDS_PER_MINUTE -> "${millis / MILLIS_PER_SECOND} с"
            value.toHours() < 1 -> "${value.toMinutes()} мин"
            value.toDays() < 1 -> "${value.toHours()} ч ${value.toMinutes() % SECONDS_PER_MINUTE} мин".replace(" 0 мин", "")
            else -> "${value.toDays()} д ${value.toHours() % HOURS_PER_DAY} ч".replace(" 0 ч", "")
        }
    }

    private const val MILLIS_PER_SECOND = 1000L
    private const val SECONDS_PER_MINUTE = 60L
    private const val HOURS_PER_DAY = 24L

    const val SHORT_ID = 8
}
