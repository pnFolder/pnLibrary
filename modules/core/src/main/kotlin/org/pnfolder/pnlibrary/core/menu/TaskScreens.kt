package org.pnfolder.pnlibrary.core.menu

import org.pnfolder.pnlibrary.api.chat.ChatTone
import org.pnfolder.pnlibrary.api.tasks.TaskSnapshot
import org.pnfolder.pnlibrary.api.tasks.TaskStatus
import org.pnfolder.pnlibrary.common.time.DisplayZone
import org.pnfolder.pnlibrary.core.commands.TaskLabels
import org.pnfolder.pnlibrary.core.commands.TaskPeriod
import org.pnfolder.pnlibrary.core.commands.TaskState
import org.pnfolder.pnlibrary.core.lifecycle.TaskControl

internal class TaskScreens(session: MenuSession, routes: MenuRoutes) : MenuScreens(session, routes) {
    /** Every task of every plugin, newest first, with two switches that show all their choices: the date and the state. */
    fun tasks(player: MenuViewer, period: TaskPeriod, state: TaskState, page: Int) {
        val nextPeriod = TaskPeriod.values()[(period.ordinal + 1) % TaskPeriod.values().size]
        val nextState = TaskState.values()[(state.ordinal + 1) % TaskState.values().size]
        val all = data.tasks()
        val inPeriod = all.filter(period::keep)
        val rows = inPeriod.filter(state::keep).sortedByDescending { TaskLabels.activity(it) }.map { task ->
            Row(taskItem(task), go { who -> taskDetail(who, task.id.value) { tasks(it, period, state, page) } })
        }
        val byDate = icon(
            Icons.CLOCK, "CLOCK", "Когда",
            Style.lore(
                listOf("Задачи за какое время показывать."), emptyList(), Style.left("переключить"),
                extra = Style.choices(TaskPeriod.values().map { "${it.label} ${all.count(it::keep)}" }, period.ordinal),
            ),
            alternative = "WATCH",
        )
        val byState = icon(
            Icons.SETTINGS, "HOPPER", "Какие",
            Style.lore(
                listOf("Какие задачи показывать."), emptyList(), Style.left("переключить"),
                extra = Style.choices(TaskState.values().map { "${it.label} ${inPeriod.count(it::keep)}" }, state.ordinal),
            ),
        )
        list(
            player, "Задачи: ${period.label}, ${state.label}", rows, page, PAGE_SIZE, { who, to -> tasks(who, period, state, to) }, { routes.home(it) },
            "Задач нет",
            extra = {
                it.button(DATE_FILTER, byDate, go { who -> tasks(who, nextPeriod, state, 0) })
                it.button(FILTER, byState, go { who -> tasks(who, period, nextState, 0) })
            },
        )
    }

    private fun taskItem(task: TaskSnapshot): ItemSpec {
        val material = when (task.status) {
            TaskStatus.SCHEDULED -> "CLOCK"
            TaskStatus.RUNNING -> "EMERALD"
            TaskStatus.COMPLETED -> "PAPER"
            TaskStatus.CANCELLED -> "COAL"
            TaskStatus.FAILED -> "BARRIER"
        }
        val description = task.description?.let { wrap(it, limit = 3) } ?: listOf("Плагин не описал эту задачу.")
        val parameters = buildList {
            add("Плагин" to ownerLine(task.ownerName))
            add("Состояние" to TaskLabels.label(task.status))
            add("Запусков" to "${task.runCount}")
            task.lastFailure?.let { add("Ошибка" to "✖ ${it.take(MESSAGE_PREVIEW)}") }
        }
        val lore = Style.lore(description, parameters, Style.left("подробности"))
        return icon(
            null, material, TaskLabels.title(task), lore, alternative = "WATCH".takeIf { material == "CLOCK" },
            danger = task.status == TaskStatus.FAILED, good = task.status == TaskStatus.RUNNING, muted = task.status == TaskStatus.CANCELLED,
        )
    }

    /** One task: what it is for, who owns it, how it runs, when; waiting tasks can be started, active ones stopped. */
    private fun taskDetail(player: MenuViewer, id: String, back: (MenuViewer) -> Unit) {
        val task = data.tasks().firstOrNull { it.id.value == id }
        if (task == null) {
            tell(player, ChatTone.INFO, "Эта задача уже исчезла из списка.")
            return back(player)
        }
        val builder = frame(Menus.chest(title("Задача")).rows(DETAIL_ROWS), DETAIL_ROWS)
        val summary = buildList {
            add("Плагин" to ownerLine(task.ownerName))
            add("Состояние" to TaskLabels.label(task.status))
            task.interval?.let { add("Повторяется" to "каждые ${TaskLabels.span(it)}") }
        }
        val about = task.description?.let { wrap(it) } ?: listOf("Плагин не описал эту задачу.")
        builder.slot(
            4,
            icon(
                null, "CLOCK", TaskLabels.title(task), Style.lore(about, summary), alternative = "WATCH",
                danger = task.status == TaskStatus.FAILED, good = task.status == TaskStatus.RUNNING,
            ),
        )
        val usage = task.usage?.let { wrap(it) } ?: listOf("Не указано. Плагин может добавить usage в TaskSpec.")
        builder.slot(20, icon(Icons.CHANGELOG, "WRITTEN_BOOK", "Как используется", Style.lore(usage, emptyList()), muted = task.usage == null))
        val runs = buildList {
            add("Запусков" to "${task.runCount}")
            add("Пропущено" to "${task.skippedCount}")
            task.lastDuration?.let { add("Последний заняла" to TaskLabels.span(it)) }
            add("Где" to TaskLabels.where(task))
        }
        builder.slot(21, icon(Icons.SETTINGS, "COMPARATOR", "Как выполняется", Style.lore(emptyList(), runs)))
        val times = buildList {
            add("Создана" to stamp(task.createdAt))
            task.nextRunAt?.let { add("Следующий запуск" to stamp(it)); add("" to ago(it)) }
            task.lastStartedAt?.let { add("Последний запуск" to stamp(it)); add("" to ago(it)) }
            task.lastCompletedAt?.let { add("Закончилась" to stamp(it)) }
            add("Пояс" to DisplayZone.zone.id)
        }
        builder.slot(22, icon(Icons.CLOCK, "CLOCK", "Время", Style.lore(emptyList(), times), alternative = "WATCH"))
        val origin = buildList {
            task.source?.let { addAll(labelled("Класс", it)) }
            task.key?.let { add("Ключ" to it) }
            if (task.tags.isNotEmpty()) addAll(labelled("Теги", task.tags.joinToString(", ")))
            add("Остановить" to TaskLabels.stopAnswer(task))
            add("Запустить сейчас" to TaskLabels.runAnswer(task))
            add("Номер" to task.id.value.take(TaskLabels.SHORT_ID))
        }
        builder.slot(23, icon(Icons.PLUGIN, "PAPER", "Откуда и права", Style.lore(emptyList(), origin)))
        task.lastFailure?.let { failure ->
            val lore = Style.lore(emptyList(), emptyList(), extra = wrap(failure).map { Style.danger(it) })
            builder.slot(24, icon(Icons.PROBLEMS, "BARRIER", "Последняя ошибка", lore, danger = true))
        }
        if (!(task.status == TaskStatus.SCHEDULED && task.runnableNow)) {
            builder.slot(
                29,
                icon(
                    Icons.PROBLEMS, "BARRIER", "Запуск закрыт",
                    Style.lore(listOf(TaskLabels.runAnswer(task).replaceFirstChar { it.uppercase() } + "."), emptyList()),
                    muted = true,
                ),
            )
        }
        if (task.status == TaskStatus.SCHEDULED && task.runnableNow) {
            builder.button(
                29,
                icon(
                    Icons.CHECK, "EMERALD", "Запустить сейчас",
                    Style.lore(
                        listOf("Выполнить один раз, не дожидаясь срока. Расписание не меняется."),
                        emptyList(), Style.left("запустить"),
                    ), good = true,
                ),
                go { who ->
                    allowed(who, TASKS) {
                        when (data.runTask(task.id.value)) {
                            TaskControl.DONE -> tell(who, ChatTone.SUCCESS, "Задача запущена.")
                            TaskControl.FORBIDDEN -> tell(who, ChatTone.FAILURE, "Плагин не разрешает запускать эту задачу вручную.")
                            TaskControl.UNAVAILABLE -> tell(who, ChatTone.INFO, "Сейчас запустить нельзя: задача не ждёт срока.")
                        }
                        taskDetail(who, id, back)
                    }
                },
            )
        }
        if (!TaskLabels.isActive(task.status)) {
            builder.slot(
                31,
                icon(
                    Icons.PROBLEMS, "BARRIER", "Остановка закрыта",
                    Style.lore(listOf(TaskLabels.stopAnswer(task).replaceFirstChar { it.uppercase() } + "."), emptyList()), muted = true,
                ),
            )
        }
        if (TaskLabels.isActive(task.status)) {
            if (task.cancellable) {
                builder.button(
                    31,
                    icon(
                        Icons.ROLLBACK, "TNT", "Остановить задачу",
                        Style.lore(listOf("Больше не будет запускаться."), emptyList(), Style.left("остановить")), danger = true,
                    ),
                    go { who ->
                        allowed(who, TASKS) {
                            when (data.cancelTask(task.id.value)) {
                                TaskControl.DONE -> tell(who, ChatTone.SUCCESS, "Задача остановлена.")
                                TaskControl.FORBIDDEN -> tell(who, ChatTone.FAILURE, "Плагин не разрешает останавливать эту задачу.")
                                TaskControl.UNAVAILABLE -> tell(who, ChatTone.INFO, "Задача уже не работает.")
                            }
                            taskDetail(who, id, back)
                        }
                    },
                )
            } else {
                builder.slot(
                    31,
                    icon(
                        Icons.PROBLEMS, "BARRIER", "Остановка закрыта",
                        Style.lore(listOf("Плагин запретил останавливать эту задачу из меню и команд."), emptyList()), muted = true,
                    ),
                )
            }
        }
        builder.button(DETAIL_BACK, backItem(), go { back(it) })
        show(player, builder.build())
    }

    /** What the event called [name] says about itself, or null when it is unknown. */
    fun aboutOf(name: String): EventAbout? = data.eventAbout(name)

    /** `Name 1.2.3` for a plugin, or the plain [name] when the server does not know it. */
    fun ownerLine(name: String): String = data.ownerNamed(name)?.let { versioned(it) } ?: name

    fun versioned(owner: OwnerInfo): String = listOfNotNull(owner.name, owner.version).joinToString(" ")

    /** The lines that say which plugin owns something: its name, version and authors. */
    fun ownerParameters(owner: OwnerInfo?): List<Pair<String, String>> = owner?.let {
        buildList {
            add("Плагин" to it.name)
            it.version?.let { version -> add("Версия" to version) }
            if (it.authors.isNotEmpty()) add("Автор" to it.authors.joinToString(", "))
        }
    } ?: listOf("Плагин" to "сервер или библиотека")

    /** [text] as `[ label ]` followed by indented lines, for a value that does not fit one line. */
    fun labelled(label: String, text: String): List<Pair<String, String>> =
        wrap(text).mapIndexed { index, line -> (if (index == 0) label else "") to line }
}
