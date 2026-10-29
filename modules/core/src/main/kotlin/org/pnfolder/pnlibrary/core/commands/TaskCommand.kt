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
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

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

/** Russian words, colours and times for tasks, shared by `/pn tasks` and the menu. */
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

    /** The latest moment something happened to the task: its last start, end or creation. */
    fun activity(task: TaskSnapshot): Instant = listOfNotNull(task.lastStartedAt, task.lastCompletedAt, task.createdAt).max()

    /** Whether something happened to the task today, in the configured time zone. */
    fun isToday(task: TaskSnapshot): Boolean =
        activity(task).atZone(DisplayZone.zone).toLocalDate() == LocalDate.now(DisplayZone.zone)

    /** The task's own name, otherwise its key, otherwise the start of its id. */
    fun title(task: TaskSnapshot): String = task.name ?: task.key ?: task.id.value.take(SHORT_ID)

    /** Whether the task can be stopped now: `да`, or `нет` with the reason. */
    fun stopAnswer(task: TaskSnapshot): String = when {
        !isActive(task.status) -> "нет, задача уже ${label(task.status)}"
        !task.cancellable -> "нет, плагин запретил"
        else -> "да"
    }

    /** Whether the task can be started at once: `да`, or `нет` with the reason. */
    fun runAnswer(task: TaskSnapshot): String = when {
        !task.runnableNow -> "нет, плагин запретил"
        task.status == TaskStatus.RUNNING -> "нет, задача выполняется"
        task.status != TaskStatus.SCHEDULED -> "нет, задача уже ${label(task.status)}"
        else -> "да"
    }

    /** Where the task runs, in words. */
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

    /** A length of time in words: `250 мс`, `3 с`, `5 мин`, `1 ч 20 мин`, `2 д 3 ч`. */
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

    /** The first characters of the id, enough to type. */
    const val SHORT_ID = 8
}

/**
 * `/pn tasks`: the scheduled tasks of every plugin, drawn like the other `/pn` pages.
 *
 * | Command | What it shows |
 * |---|---|
 * | `/pn tasks [today\|week\|older\|any] [all\|active\|failed\|done] [page]` | tasks with tabs by date and by state; today by default |
 * | `/pn tasks view <id>` | one task: what it is for, owner, how it runs, times, rights and the last failure |
 * | `/pn tasks run <id>` | starts a waiting task at once, unless its plugin forbids it |
 * | `/pn tasks cancel <id>` | stops a task that has not finished, unless its plugin forbids it |
 *
 * A task may carry a description and a usage note given by its plugin, and may be hidden from this command. The id may
 * be shortened to its first characters. Requires `pnlibrary.tasks`; the console always may use it.
 */
class TaskCommand(private val library: PnLibrary) {
    /** Builds the command tree to mount under `/pn tasks`. */
    fun definition(): CommandDefinition = command("tasks") {
        permission(PERMISSION)
        noPermissionMessage("Задачи плагинов доступны администраторам. Нужно право $PERMISSION.")
        consoleBypassesPermission()
        executes { list(it, TaskPeriod.TODAY, TaskState.ALL, 1) }
        TaskPeriod.values().forEach { period ->
            literal(period.word) {
                executes { list(it, period, TaskState.ALL, 1) }
                argument("page", ArgumentType.integer()) { executes { list(it, period, TaskState.ALL, it.get<Int>("page")) } }
                TaskState.values().forEach { state ->
                    literal(state.word) {
                        executes { list(it, period, state, 1) }
                        argument("page", ArgumentType.integer()) { executes { list(it, period, state, it.get<Int>("page")) } }
                    }
                }
            }
        }
        TaskState.values().forEach { state ->
            literal(state.word) {
                executes { list(it, TaskPeriod.ANY, state, 1) }
                argument("page", ArgumentType.integer()) { executes { list(it, TaskPeriod.ANY, state, it.get<Int>("page")) } }
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

    private fun missing(kit: ChatKit, typed: String): Component =
        kit.text("Такой задачи нет или id неоднозначен: $typed. Список: $ROOT", ChatPalette.DANGER)

    // --- the list ------------------------------------------------------------------------------------------------

    private fun list(context: CommandContext, period: TaskPeriod, state: TaskState, requestedPage: Int) {
        val kit = kit(context)
        val all = library.taskSnapshots().sortedByDescending { TaskLabels.activity(it) }
        val inPeriod = all.filter(period::keep)
        val shown = inPeriod.filter(state::keep)
        val pages = ((shown.size + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
        val page = requestedPage.coerceIn(1, pages)
        val count = "${all.size} ${RussianText.plural(all.size.toLong(), "задача", "задачи", "задач")}"
        val lines = mutableListOf(kit.blank(), kit.header("Задачи плагинов", kit.text(count, ChatPalette.LABEL)))
        lines += tabs(kit, "Когда", TaskPeriod.values().toList(), period, { it.label }, { p -> all.count(p::keep) }) { "$ROOT ${it.word} ${state.word}" }
        lines += tabs(kit, "Какие", TaskState.values().toList(), state, { it.label }, { s -> inPeriod.count(s::keep) }) { "$ROOT ${period.word} ${it.word}" }
        lines += kit.blank()
        if (shown.isEmpty()) lines += kit.note("Здесь пока пусто.", ChatPalette.FAINT)
        shown.drop((page - 1) * PAGE_SIZE).take(PAGE_SIZE).forEach { lines += row(kit, it) }
        if (pages > 1) {
            lines += kit.blank()
            lines += pager(kit, "$ROOT ${period.word} ${state.word}", page, pages)
        }
        if (!kit.reader.interactive) {
            lines += kit.blank()
            lines += kit.commandHints(
                "$ROOT [today|week|older|any] [all|active|failed|done] [страница]" to "список задач",
                "$ROOT view <id>" to "карточка задачи",
                "$ROOT run <id>" to "запустить сейчас",
                "$ROOT cancel <id>" to "остановить задачу",
            )
        }
        lines.forEach(context.sender::send)
    }

    /** One row of tabs: [title], then a button per choice with its count; the current one is marked. */
    private fun <T> tabs(
        kit: ChatKit,
        title: String,
        choices: List<T>,
        current: T,
        label: (T) -> String,
        count: (T) -> Int,
        command: (T) -> String,
    ): Component {
        var line = kit.text(" $title: ", ChatPalette.LABEL)
        choices.forEachIndexed { index, choice ->
            if (index > 0) line = line.append(kit.text(" "))
            val text = "${label(choice)} ${count(choice)}"
            val tab = if (choice == current) {
                kit.selected(text, ChatPalette.ACCENT)
            } else {
                kit.option(text, command(choice), "Показать: ${label(choice)}", ChatPalette.SOFT)
            }
            line = line.append(tab)
        }
        return line
    }

    /** Two or three lines per task: the title with its state, the plugin, runs and last activity, then the description. */
    private fun row(kit: ChatKit, task: TaskSnapshot): Component {
        val title = kit.text(" ").append(kit.text(kit.glyph('●'), TaskLabels.color(task.status)))
            .append(kit.text(" ${TaskLabels.title(task)}", ChatPalette.ACCENT))
            .append(kit.text("  ${TaskLabels.label(task.status)}", TaskLabels.color(task.status)))
        val runs = "${task.runCount} ${RussianText.plural(task.runCount, "запуск", "запуска", "запусков")}"
        var facts = kit.text("    ${task.ownerName}", ChatPalette.TEXT)
            .append(kit.dot()).append(kit.text(runs, ChatPalette.SOFT))
            .append(kit.dot()).append(kit.text(TaskLabels.ago(TaskLabels.activity(task)), ChatPalette.FAINT))
        if (task.lastFailure != null) facts = facts.append(kit.dot()).append(kit.text("ошибка", ChatPalette.DANGER))
        val lines = mutableListOf(title, facts)
        task.description?.let { lines += kit.text("    ${it.take(DESCRIPTION_PREVIEW)}", ChatPalette.LABEL) }
        val block = lines.drop(1).fold(lines.first()) { all, line -> all.append(Component.newline()).append(line) }
        if (!kit.reader.interactive) return block
        val hover = mutableListOf(kit.text(TaskLabels.title(task), ChatPalette.ACCENT))
        task.description?.let { hover += kit.text(it, ChatPalette.TEXT) }
        hover += kit.blank()
        hover += kit.hoverField("Состояние", kit.text(TaskLabels.label(task.status), TaskLabels.color(task.status)))
        hover += kit.hoverField("Плагин", kit.text(task.ownerName, ChatPalette.TEXT))
        hover += kit.hoverField("Последнее", kit.text(TaskLabels.moment(TaskLabels.activity(task)), ChatPalette.TEXT))
        hover += kit.blank()
        hover += kit.text("Нажмите, чтобы открыть задачу ›", ChatPalette.ACCENT)
        return block.hoverEvent(kit.tooltip(*hover.toTypedArray())).clickEvent(ClickEvent.runCommand("$ROOT view ${task.id.value}"))
    }

    // --- one task ------------------------------------------------------------------------------------------------

    private fun view(context: CommandContext, typed: String) {
        val kit = kit(context)
        val task = find(typed)
        if (task == null) {
            context.sender.send(missing(kit, typed))
            return
        }
        val lines = mutableListOf(
            kit.blank(),
            kit.header(TaskLabels.title(task), kit.bracketed(TaskLabels.label(task.status), TaskLabels.color(task.status))),
        )
        task.description?.let { lines += kit.note(it, ChatPalette.TEXT) }
        task.usage?.let { lines += kit.note("Как используется: $it", ChatPalette.LABEL) }
        if (task.description == null && task.usage == null) {
            lines += kit.note("Плагин не описал эту задачу. Описание задаётся в TaskSpec: description и usage.", ChatPalette.FAINT)
        }
        lines += kit.blank()
        lines += kit.section("Кто и как")
        lines += kit.field("Плагин", kit.text(task.ownerName, ChatPalette.TEXT))
        task.source?.let { lines += kit.field("Класс", kit.text(it, ChatPalette.TEXT)) }
        task.key?.let { lines += kit.field("Ключ", kit.text(it, ChatPalette.TEXT)) }
        if (task.tags.isNotEmpty()) lines += kit.field("Теги", kit.text(task.tags.joinToString(", "), ChatPalette.SOFT))
        lines += kit.field("Выполняется", kit.text(TaskLabels.where(task), ChatPalette.TEXT))
        task.interval?.let { lines += kit.field("Повторяется", kit.text("каждые ${TaskLabels.span(it)}", ChatPalette.TEXT)) }
        lines += kit.field("Запусков", kit.text("${task.runCount}, пропущено ${task.skippedCount}", ChatPalette.TEXT))
        task.lastDuration?.let { lines += kit.field("Последний заняла", kit.text(TaskLabels.span(it), ChatPalette.TEXT)) }
        lines += kit.blank()
        lines += kit.section("Когда")
        lines += kit.field("Создана", moment(kit, task.createdAt))
        task.nextRunAt?.let { lines += kit.field("Следующий запуск", moment(kit, it)) }
        task.lastStartedAt?.let { lines += kit.field("Последний запуск", moment(kit, it)) }
        task.lastCompletedAt?.let { lines += kit.field("Закончилась", moment(kit, it)) }
        lines += kit.field("Часовой пояс", kit.text(DisplayZone.zone.id, ChatPalette.FAINT))
        lines += kit.blank()
        lines += kit.section("Права")
        lines += kit.field("Остановить", answer(kit, TaskLabels.stopAnswer(task)))
        lines += kit.field("Запустить сейчас", answer(kit, TaskLabels.runAnswer(task)))
        task.lastFailure?.let {
            lines += kit.blank()
            lines += kit.section("Ошибка")
            lines += kit.note(it, ChatPalette.DANGER)
        }
        lines += kit.blank()
        kit.actions(kit.button("назад", ROOT, "К списку задач", ChatPalette.ACCENT), start(kit, task), stop(kit, task))?.let { lines += it }
        lines.forEach(context.sender::send)
    }

    private fun moment(kit: ChatKit, at: Instant): Component =
        kit.text(TaskLabels.moment(at), ChatPalette.TEXT).append(kit.text("  ${TaskLabels.ago(at)}", ChatPalette.FAINT))

    private fun answer(kit: ChatKit, text: String): Component = kit.text(text, if (text == "да") ChatPalette.OK else ChatPalette.SOFT)

    /** The run button, or a grey one that says why running is closed. */
    private fun start(kit: ChatKit, task: TaskSnapshot): Component? {
        if (task.status == TaskStatus.SCHEDULED && task.runnableNow) {
            return kit.button("запустить сейчас", "$ROOT run ${task.id.value}", "Выполнить, не дожидаясь срока", ChatPalette.OK)
        }
        return closed(kit, "запуск закрыт", TaskLabels.runAnswer(task))
    }

    /** The stop button, or a grey one that says why stopping is closed. */
    private fun stop(kit: ChatKit, task: TaskSnapshot): Component? {
        if (TaskLabels.isActive(task.status) && task.cancellable) {
            return kit.button("остановить", "$ROOT cancel ${task.id.value}", "Остановить задачу", ChatPalette.DANGER)
        }
        return closed(kit, "остановка закрыта", TaskLabels.stopAnswer(task))
    }

    private fun closed(kit: ChatKit, label: String, reason: String): Component? {
        if (!kit.reader.interactive) return null
        val why = kit.text(reason.replaceFirstChar { it.uppercase() }, ChatPalette.SOFT)
        return kit.bracketed(label, ChatPalette.FAINT).hoverEvent(kit.tooltip(why))
    }

    // --- run and cancel ------------------------------------------------------------------------------------------

    private fun cancel(context: CommandContext, typed: String) {
        val kit = kit(context)
        val task = find(typed)
        if (task == null) {
            context.sender.send(missing(kit, typed))
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
            context.sender.send(missing(kit, typed))
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

    /** Constants of the `/pn tasks` command. */
    companion object {
        /** Permission of the whole `/pn tasks` tree. */
        const val PERMISSION = "pnlibrary.tasks"
        private const val ROOT = "/pn tasks"
        private const val PAGE_SIZE = 6
        private const val DESCRIPTION_PREVIEW = 70
    }
}
