package org.pnfolder.pnlibrary.core.commands.tasks

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
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
import org.pnfolder.pnlibrary.core.commands.chatPager
import org.pnfolder.pnlibrary.core.lifecycle.cancelTask
import org.pnfolder.pnlibrary.core.lifecycle.runTaskNow
import org.pnfolder.pnlibrary.core.lifecycle.taskSnapshots
import org.pnfolder.pnlibrary.core.tasks.TaskControl

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

    /** Runs [action] for the one task whose id starts with [typed], or says that there is no such single task. */
    private fun withTask(context: CommandContext, typed: String, action: (ChatKit, TaskSnapshot) -> Unit) {
        val kit = kit(context)
        val task = library.taskSnapshots().singleOrNull { it.id.value.startsWith(typed, ignoreCase = true) }
        if (task == null) {
            context.sender.send(kit.text("Такой задачи нет или id неоднозначен: $typed. Список: $ROOT", ChatPalette.DANGER))
            return
        }
        action(kit, task)
    }

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
            lines += chatPager(kit, "$ROOT ${period.word} ${state.word}", page, pages)
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

    private fun view(context: CommandContext, typed: String) = withTask(context, typed) { kit, task ->
        val lines = mutableListOf(
            kit.blank(),
            kit.header(TaskLabels.title(task), kit.bracketed(TaskLabels.label(task.status), TaskLabels.color(task.status))),
        )
        task.description?.let { lines += kit.note(it, ChatPalette.TEXT) }
        task.usage?.let { lines += kit.note("Как используется: $it", ChatPalette.LABEL) }
        if (task.description == null && task.usage == null) {
            lines += kit.note("Плагин не описал эту задачу. Описание задаётся в TaskSpec: description и usage.", ChatPalette.FAINT)
        }
        lines += TaskDetail.who(kit, task)
        lines += TaskDetail.whenLines(kit, task)
        lines += TaskDetail.rights(kit, task)
        task.lastFailure?.let {
            lines += kit.blank()
            lines += kit.section("Ошибка")
            lines += kit.note(it, ChatPalette.DANGER)
        }
        lines += kit.blank()
        kit.actions(kit.button("назад", ROOT, "К списку задач", ChatPalette.ACCENT), start(kit, task), stop(kit, task))?.let { lines += it }
        lines.forEach(context.sender::send)
    }

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

    private fun cancel(context: CommandContext, typed: String) = withTask(context, typed) { kit, task ->
        val title = TaskLabels.title(task)
        context.sender.send(
            when (library.cancelTask(task.id.value)) {
                TaskControl.DONE -> kit.text("Задача $title остановлена.", ChatPalette.OK)
                TaskControl.FORBIDDEN -> kit.text("Плагин не разрешает останавливать задачу $title.", ChatPalette.DANGER)
                TaskControl.UNAVAILABLE -> kit.text("Задача $title уже не работает.", ChatPalette.SOFT)
            },
        )
    }

    private fun run(context: CommandContext, typed: String) = withTask(context, typed) { kit, task ->
        val title = TaskLabels.title(task)
        context.sender.send(
            when (library.runTaskNow(task.id.value)) {
                TaskControl.DONE -> kit.text("Задача $title запущена.", ChatPalette.OK)
                TaskControl.FORBIDDEN -> kit.text("Плагин не разрешает запускать задачу $title вручную.", ChatPalette.DANGER)
                TaskControl.UNAVAILABLE -> kit.text("Задачу $title сейчас запустить нельзя: она не ждёт срока.", ChatPalette.SOFT)
            },
        )
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
