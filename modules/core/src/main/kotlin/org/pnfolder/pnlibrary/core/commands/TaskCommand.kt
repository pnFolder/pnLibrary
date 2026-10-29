package org.pnfolder.pnlibrary.core.commands

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.TextColor
import org.pnfolder.pnlibrary.api.chat.ChatKit
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.chat.ChatReader
import org.pnfolder.pnlibrary.api.commands.ArgumentType
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.command
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.tasks.TaskSnapshot
import org.pnfolder.pnlibrary.api.tasks.TaskStatus
import org.pnfolder.pnlibrary.common.text.RussianText
import org.pnfolder.pnlibrary.common.time.DisplayZone
import org.pnfolder.pnlibrary.core.lifecycle.TaskControl
import org.pnfolder.pnlibrary.core.lifecycle.cancelTask
import org.pnfolder.pnlibrary.core.lifecycle.runTaskNow
import org.pnfolder.pnlibrary.core.lifecycle.taskSnapshots
import java.time.Instant
import java.time.format.DateTimeFormatter

/** Russian words and colours for the state of a task, shared by `/pn tasks` and the menu. */
object TaskLabels {
    /** The state of [status] in words. */
    fun label(status: TaskStatus): String = when (status) {
        TaskStatus.SCHEDULED -> "ожидает запуска"
        TaskStatus.RUNNING -> "выполняется"
        TaskStatus.COMPLETED -> "завершена"
        TaskStatus.CANCELLED -> "отменена"
        TaskStatus.FAILED -> "упала"
    }

    /** The chat colour that goes with [status]. */
    fun color(status: TaskStatus): TextColor = when (status) {
        TaskStatus.SCHEDULED -> ChatPalette.INFO
        TaskStatus.RUNNING -> ChatPalette.OK
        TaskStatus.COMPLETED -> ChatPalette.SOFT
        TaskStatus.CANCELLED -> ChatPalette.FAINT
        TaskStatus.FAILED -> ChatPalette.DANGER
    }

    /** Whether the task can still run, so it can be cancelled. */
    fun isActive(status: TaskStatus): Boolean = status == TaskStatus.SCHEDULED || status == TaskStatus.RUNNING

    /** The task's own name, otherwise its key, otherwise the start of its id. */
    fun title(task: TaskSnapshot): String = task.name ?: task.key ?: task.id.value.take(SHORT_ID)

    /** A length of time in words: `250 мс`, `3 с`, `5 мин`, `1 ч 20 мин`, `2 д 3 ч`. */
    fun span(value: java.time.Duration): String {
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

    /** The first characters of the id, enough to type. */
    const val SHORT_ID = 8
}

/**
 * `/pn tasks`: the scheduled tasks of every plugin, drawn like the other `/pn` pages.
 *
 * | Command | What it shows |
 * |---|---|
 * | `/pn tasks [all\|active\|failed\|done] [page]` | tasks with tabs; click a row to open it |
 * | `/pn tasks view <id>` | one task: what it is for, how it is used, owner, runs, times and the last failure |
 * | `/pn tasks cancel <id>` | stops a task that has not finished, unless its plugin forbids it |
 * | `/pn tasks run <id>` | starts a waiting repeating task at once, unless its plugin forbids it |
 *
 * A task may carry a description and a usage note given by its plugin; both are optional. The id may be shortened to
 * its first characters. Requires `pnlibrary.tasks`; the console always may use it.
 */
class TaskCommand(private val library: PnLibrary) {
    /** Builds the command tree to mount under `/pn tasks`. */
    fun definition(): CommandDefinition = command("tasks") {
        permission(PERMISSION)
        noPermissionMessage("Задачи плагинов доступны администраторам. Нужно право $PERMISSION.")
        consoleBypassesPermission()
        executes { list(it, "all", 1) }
        FILTERS.forEach { (word, _) ->
            literal(word) {
                executes { list(it, word, 1) }
                argument("page", ArgumentType.integer()) { executes { list(it, word, it.get<Int>("page")) } }
            }
        }
        literal("view") {
            argument("id", ArgumentType.string()) {
                suggests { library.taskSnapshots().map { it.id.value.take(TaskLabels.SHORT_ID) } }
                executes { view(it, it.get<String>("id")) }
            }
        }
        literal("run") {
            argument("id", ArgumentType.string()) {
                suggests {
                    library.taskSnapshots().filter { it.status == TaskStatus.SCHEDULED && it.runnableNow }.map { it.id.value.take(TaskLabels.SHORT_ID) }
                }
                executes { run(it, it.get<String>("id")) }
            }
        }
        literal("cancel") {
            argument("id", ArgumentType.string()) {
                suggests { library.taskSnapshots().filter { TaskLabels.isActive(it.status) }.map { it.id.value.take(TaskLabels.SHORT_ID) } }
                executes { cancel(it, it.get<String>("id")) }
            }
        }
    }

    private fun kit(context: CommandContext) = ChatKit(ChatReader.of(context.sender))

    private fun find(typed: String): TaskSnapshot? =
        library.taskSnapshots().filter { it.id.value.startsWith(typed, ignoreCase = true) }.singleOrNull()

    private fun list(context: CommandContext, word: String, requestedPage: Int) {
        val kit = kit(context)
        val all = library.taskSnapshots().sortedByDescending { it.createdAt }
        val shown = all.filter(FILTERS.first { it.first == word }.second)
        val pages = ((shown.size + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
        val page = requestedPage.coerceIn(1, pages)
        val count = "${all.size} ${RussianText.plural(all.size.toLong(), "задача", "задачи", "задач")}"
        val lines = mutableListOf(kit.blank(), kit.header("Задачи плагинов", kit.text(count, ChatPalette.LABEL)), tabs(kit, all, word), kit.blank())
        if (shown.isEmpty()) lines += kit.note("Здесь пока пусто.")
        shown.drop((page - 1) * PAGE_SIZE).take(PAGE_SIZE).forEach { lines += row(kit, it) }
        if (pages > 1) {
            lines += kit.blank()
            lines += pager(kit, "$ROOT $word", page, pages)
        }
        if (!kit.reader.interactive) {
            lines += kit.blank()
            lines += kit.commandHints(
                "$ROOT [all|active|failed|done] [страница]" to "список задач",
                "$ROOT view <id>" to "карточка задачи",
                "$ROOT run <id>" to "запустить сейчас",
                "$ROOT cancel <id>" to "остановить задачу",
            )
        }
        lines.forEach(context.sender::send)
    }

    private fun tabs(kit: ChatKit, all: List<TaskSnapshot>, current: String): Component {
        var line = kit.text(" Показать: ", ChatPalette.LABEL)
        FILTERS.forEachIndexed { index, (word, predicate) ->
            if (index > 0) line = line.append(kit.text(" "))
            val count = all.count(predicate)
            val open = word == current
            val body = if (open) ChatPalette.ACCENT else if (count == 0) ChatPalette.FAINT else ChatPalette.SOFT
            val button = kit.text("[", ChatPalette.FAINT).append(kit.text("${LABELS.getValue(word)} $count", body)).append(kit.text("]", ChatPalette.FAINT))
            line = line.append(
                if (open || !kit.reader.interactive) button
                else button.clickEvent(ClickEvent.runCommand("$ROOT $word")).hoverEvent(kit.tooltip(kit.text("$ROOT $word", ChatPalette.LABEL))),
            )
        }
        return line
    }

    private fun row(kit: ChatKit, task: TaskSnapshot): Component {
        var line = kit.text(" ").append(kit.text(kit.glyph('●'), TaskLabels.color(task.status)))
            .append(kit.text(" ${TaskLabels.title(task)}", ChatPalette.ACCENT))
            .append(kit.text("  ${task.ownerName}", ChatPalette.TEXT))
            .append(kit.text("  ${task.runCount} ${RussianText.plural(task.runCount, "запуск", "запуска", "запусков")}", ChatPalette.SOFT))
        if (task.lastFailure != null) line = line.append(kit.text("  ошибка", ChatPalette.DANGER))
        if (!kit.reader.interactive) return line
        val hover = mutableListOf(kit.text(TaskLabels.title(task), ChatPalette.ACCENT))
        task.description?.let { hover += kit.text(it, ChatPalette.TEXT) }
        hover += kit.blank()
        hover += kit.hoverField("Состояние", kit.text(TaskLabels.label(task.status), TaskLabels.color(task.status)))
        hover += kit.hoverField("Владелец", kit.text(task.ownerName, ChatPalette.TEXT))
        hover += kit.blank()
        hover += kit.text("Нажмите, чтобы открыть задачу ›", ChatPalette.ACCENT)
        return line.hoverEvent(kit.tooltip(*hover.toTypedArray())).clickEvent(ClickEvent.runCommand("$ROOT view ${task.id.value}"))
    }

    private fun view(context: CommandContext, typed: String) {
        val kit = kit(context)
        val task = find(typed)
        if (task == null) {
            context.sender.send(kit.text("Такой задачи нет или id неоднозначен: $typed. Список: $ROOT", ChatPalette.DANGER))
            return
        }
        val lines = mutableListOf(
            kit.blank(),
            kit.header(TaskLabels.title(task), kit.text(TaskLabels.label(task.status), TaskLabels.color(task.status))),
            kit.blank(),
        )
        task.description?.let { lines += kit.field("Для чего", kit.text(it, ChatPalette.TEXT)) }
        task.usage?.let { lines += kit.field("Как используется", kit.text(it, ChatPalette.TEXT)) }
        if (task.description == null && task.usage == null) {
            lines += kit.note("Плагин не описал эту задачу. Описание задаётся в TaskSpec: description и usage.", ChatPalette.FAINT)
        }
        lines += kit.field("Владелец", kit.text(task.ownerName, ChatPalette.TEXT))
        task.source?.let { lines += kit.field("Класс", kit.text(it, ChatPalette.TEXT)) }
        task.key?.let { lines += kit.field("Ключ", kit.text(it, ChatPalette.TEXT)) }
        lines += kit.field("Выполняется", kit.text(task.executionKind.name.lowercase(), ChatPalette.TEXT))
        lines += kit.field("Запусков", kit.text("${task.runCount}, пропущено ${task.skippedCount}", ChatPalette.TEXT))
        task.interval?.let { lines += kit.field("Повторяется", kit.text("каждые ${TaskLabels.span(it)}", ChatPalette.TEXT)) }
        lines += kit.field("Создана", kit.text(time(task.createdAt), ChatPalette.TEXT))
        task.nextRunAt?.let { lines += kit.field("Следующий запуск", kit.text(time(it), ChatPalette.TEXT)) }
        task.lastStartedAt?.let { lines += kit.field("Последний запуск", kit.text(time(it), ChatPalette.TEXT)) }
        task.lastDuration?.let { lines += kit.field("Заняла", kit.text(TaskLabels.span(it), ChatPalette.TEXT)) }
        task.lastCompletedAt?.let { lines += kit.field("Закончилась", kit.text(time(it), ChatPalette.TEXT)) }
        lines += kit.field("Часовой пояс", kit.text(DisplayZone.zone.id, ChatPalette.FAINT))
        if (task.tags.isNotEmpty()) lines += kit.field("Теги", kit.text(task.tags.joinToString(", "), ChatPalette.SOFT))
        task.lastFailure?.let { lines += kit.field("Ошибка", kit.text(it, ChatPalette.DANGER)) }
        lines += kit.blank()
        if (!task.cancellable && TaskLabels.isActive(task.status)) lines += kit.note("Плагин не разрешает останавливать эту задачу.", ChatPalette.FAINT)
        val stop = if (TaskLabels.isActive(task.status) && task.cancellable) {
            kit.button("отменить", "$ROOT cancel ${task.id.value}", "Остановить задачу", ChatPalette.DANGER)
        } else {
            null
        }
        val start = if (task.status == TaskStatus.SCHEDULED && task.runnableNow) {
            kit.button("запустить сейчас", "$ROOT run ${task.id.value}", "Выполнить, не дожидаясь срока", ChatPalette.OK)
        } else {
            null
        }
        kit.actions(kit.button("назад", ROOT, "К списку задач", ChatPalette.ACCENT), start, stop)?.let { lines += it }
        lines.forEach(context.sender::send)
    }

    private fun cancel(context: CommandContext, typed: String) {
        val kit = kit(context)
        val task = find(typed)
        if (task == null) {
            context.sender.send(kit.text("Такой задачи нет или id неоднозначен: $typed. Список: $ROOT", ChatPalette.DANGER))
            return
        }
        val title = TaskLabels.title(task)
        context.sender.send(
            when (library.cancelTask(task.id.value)) {
                TaskControl.DONE -> kit.text("Задача $title остановлена.", ChatPalette.OK)
                TaskControl.FORBIDDEN -> kit.text("Плагин не разрешает останавливать задачу $title.", ChatPalette.DANGER)
                TaskControl.UNAVAILABLE -> kit.text("Задача $title уже не работает.", ChatPalette.SOFT)
            },
        )
    }

    private fun run(context: CommandContext, typed: String) {
        val kit = kit(context)
        val task = find(typed)
        if (task == null) {
            context.sender.send(kit.text("Такой задачи нет или id неоднозначен: $typed. Список: $ROOT", ChatPalette.DANGER))
            return
        }
        val title = TaskLabels.title(task)
        context.sender.send(
            when (library.runTaskNow(task.id.value)) {
                TaskControl.DONE -> kit.text("Задача $title запущена.", ChatPalette.OK)
                TaskControl.FORBIDDEN -> kit.text("Плагин не разрешает запускать задачу $title вручную.", ChatPalette.DANGER)
                TaskControl.UNAVAILABLE -> kit.text("Задачу $title сейчас запустить нельзя: она не ждёт срока.", ChatPalette.SOFT)
            },
        )
    }

    private fun pager(kit: ChatKit, base: String, page: Int, pages: Int): Component {
        fun link(target: Int, label: String) = kit.option(label, "$base $target", "Страница $target из $pages", ChatPalette.SOFT)
        var line = kit.text("  ")
        line = line.append(if (page > 1) link(page - 1, "‹ назад") else kit.text("[‹ назад]", ChatPalette.FAINT))
        line = line.append(kit.text("  $page / $pages  ", ChatPalette.ACCENT))
        return line.append(if (page < pages) link(page + 1, "дальше ›") else kit.text("[дальше ›]", ChatPalette.FAINT))
    }

    private fun time(moment: Instant): String = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(DisplayZone.zone).format(moment)

    /** Constants of the `/pn tasks` command. */
    companion object {
        /** Permission of the whole `/pn tasks` tree. */
        const val PERMISSION = "pnlibrary.tasks"
        private const val ROOT = "/pn tasks"
        private const val PAGE_SIZE = 8

        private val FILTERS: List<Pair<String, (TaskSnapshot) -> Boolean>> = listOf(
            "all" to { _: TaskSnapshot -> true },
            "active" to { t: TaskSnapshot -> TaskLabels.isActive(t.status) },
            "failed" to { t: TaskSnapshot -> t.status == TaskStatus.FAILED || t.lastFailure != null },
            "done" to { t: TaskSnapshot -> t.status == TaskStatus.COMPLETED || t.status == TaskStatus.CANCELLED },
        )
        private val LABELS = mapOf("all" to "все", "active" to "работают", "failed" to "с ошибками", "done" to "закончены")
    }
}
