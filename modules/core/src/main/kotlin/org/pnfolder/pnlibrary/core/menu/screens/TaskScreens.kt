package org.pnfolder.pnlibrary.core.menu.screens

import org.pnfolder.pnlibrary.api.chat.ChatTone
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot
import org.pnfolder.pnlibrary.api.tasks.info.TaskStatus
import org.pnfolder.pnlibrary.common.time.DisplayZone
import org.pnfolder.pnlibrary.core.commands.tasks.TaskLabels
import org.pnfolder.pnlibrary.core.commands.tasks.TaskPeriod
import org.pnfolder.pnlibrary.core.commands.tasks.TaskState
import org.pnfolder.pnlibrary.core.menu.ItemSpec
import org.pnfolder.pnlibrary.core.menu.MenuBuilder
import org.pnfolder.pnlibrary.core.menu.MenuRoutes
import org.pnfolder.pnlibrary.core.menu.MenuScreens
import org.pnfolder.pnlibrary.core.menu.MenuSession
import org.pnfolder.pnlibrary.core.menu.MenuViewer
import org.pnfolder.pnlibrary.core.menu.Menus
import org.pnfolder.pnlibrary.core.menu.OwnerInfo
import org.pnfolder.pnlibrary.core.menu.data.EventAbout
import org.pnfolder.pnlibrary.core.menu.style.DATE_FILTER
import org.pnfolder.pnlibrary.core.menu.style.DETAIL_BACK
import org.pnfolder.pnlibrary.core.menu.style.DETAIL_ROWS
import org.pnfolder.pnlibrary.core.menu.style.FILTER
import org.pnfolder.pnlibrary.core.menu.style.Icons
import org.pnfolder.pnlibrary.core.menu.style.MESSAGE_PREVIEW
import org.pnfolder.pnlibrary.core.menu.style.PAGE_SIZE
import org.pnfolder.pnlibrary.core.menu.style.Row
import org.pnfolder.pnlibrary.core.menu.style.Style
import org.pnfolder.pnlibrary.core.menu.style.TASKS
import org.pnfolder.pnlibrary.core.menu.style.ago
import org.pnfolder.pnlibrary.core.menu.style.backItem
import org.pnfolder.pnlibrary.core.menu.style.clockStamp
import org.pnfolder.pnlibrary.core.menu.style.icon
import org.pnfolder.pnlibrary.core.menu.style.menuFrame
import org.pnfolder.pnlibrary.core.menu.style.menuTitle
import org.pnfolder.pnlibrary.core.tasks.TaskControl
import org.pnfolder.pnlibrary.core.text.wrapText

/** Menu screens for scheduled tasks. */
internal class TaskScreens(session: MenuSession, routes: MenuRoutes) : MenuScreens(session, routes) {
    fun tasks(player: MenuViewer, period: TaskPeriod, state: TaskState, page: Int) {
        val nextPeriod = TaskPeriod.values()[(period.ordinal + 1) % TaskPeriod.values().size]
        val nextState = TaskState.values()[(state.ordinal + 1) % TaskState.values().size]
        val everything = data.tasks()
        val all = TaskLabels.roots(everything)
        val inPeriod = all.filter(period::keep)
        val rows = inPeriod.filter(state::keep).sortedByDescending { TaskLabels.activity(it) }.map { task ->
            Row(taskItem(task, everything), go { who -> taskDetail(who, task.id.value) { tasks(it, period, state, page) } })
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

    private fun taskItem(task: TaskSnapshot, all: Collection<TaskSnapshot>): ItemSpec {
        val material = when (task.status) {
            TaskStatus.SCHEDULED -> "CLOCK"
            TaskStatus.RUNNING -> "EMERALD"
            TaskStatus.COMPLETED -> "PAPER"
            TaskStatus.CANCELLED -> "COAL"
            TaskStatus.FAILED -> "BARRIER"
        }
        val description = task.description?.let { wrapText(it, limit = 3) } ?: listOf("Плагин не описал эту задачу.")
        val parameters = buildList {
            add("Плагин" to ownerLine(task.ownerName))
            add("Состояние" to TaskLabels.label(task.status))
            add("Запусков" to "${task.runCount}")
            TaskLabels.children(task, all).takeIf { it > 0 }?.let { add("Подзадач" to TaskLabels.childrenLine(task, all)) }
            TaskLabels.parent(task, all)?.let { add("Подзадача от" to it) }
            TaskLabels.lag(task)?.let { add("Опоздание" to it) }
            task.lastFailure?.let { add("Ошибка" to "✖ ${it.take(MESSAGE_PREVIEW)}") }
        }
        val lore = Style.lore(description, parameters, Style.left("подробности"))
        return icon(
            null, material, TaskLabels.title(task), lore, alternative = "WATCH".takeIf { material == "CLOCK" },
            danger = task.status == TaskStatus.FAILED, good = task.status == TaskStatus.RUNNING, muted = task.status == TaskStatus.CANCELLED,
        )
    }

    private fun taskDetail(player: MenuViewer, id: String, back: (MenuViewer) -> Unit) {
        val task = data.tasks().firstOrNull { it.id.value == id }
        if (task == null) {
            tell(player, ChatTone.INFO, "Эта задача уже исчезла из списка.")
            return back(player)
        }
        val builder = menuFrame(Menus.chest(menuTitle("Задача")).rows(DETAIL_ROWS), DETAIL_ROWS)
        taskSummary(builder, task)
        taskFacts(builder, task)
        relations(builder, task, back)
        runControl(builder, task, back)
        stopControl(builder, task, back)
        builder.button(DETAIL_BACK, backItem(), go { back(it) })
        show(player, builder.build())
    }

    /** Slot 33: the parent of a subtask, or the list of subtasks of a task that started some. */
    private fun relations(builder: MenuBuilder, task: TaskSnapshot, back: (MenuViewer) -> Unit) {
        val all = data.tasks()
        val parent = task.parentId?.let { id -> all.firstOrNull { it.id == id } }
        if (parent != null) {
            val item = icon(
                Icons.ROLLBACK, "ARROW", "Подзадача от: ${TaskLabels.title(parent)}",
                Style.lore(
                    listOf("Эту задачу запустила другая задача."),
                    listOf("Состояние" to TaskLabels.label(parent.status)),
                    Style.left("открыть родителя"),
                ),
            )
            builder.button(33, item, go { who -> taskDetail(who, parent.id.value, back) })
        }
        val children = TaskLabels.childrenOf(task, all)
        if (children.isNotEmpty()) {
            val item = icon(
                Icons.CHANGELOG, "CHEST", "Подзадачи: ${children.size}",
                Style.lore(listOf("Задачи, которые запустила эта задача."), listOf("Активных" to "${children.count { it.isActive }}"), Style.left("показать")),
            )
            builder.button(34, item, go { who -> subtasks(who, task.id.value, back) })
        }
    }

    private fun subtasks(player: MenuViewer, parentId: String, back: (MenuViewer) -> Unit) {
        val all = data.tasks()
        val parent = all.firstOrNull { it.id.value == parentId } ?: return back(player)
        val rows = TaskLabels.childrenOf(parent, all).sortedByDescending { TaskLabels.activity(it) }.map { task ->
            Row(taskItem(task, all), go { who -> taskDetail(who, task.id.value) { subtasks(it, parentId, back) } })
        }
        list(
            player, "Подзадачи: ${TaskLabels.title(parent)}", rows, 0, PAGE_SIZE, { who, _ -> subtasks(who, parentId, back) },
            { taskDetail(it, parentId, back) }, "Подзадач нет",
        )
    }

    private fun taskSummary(builder: MenuBuilder, task: TaskSnapshot) {
        val summary = buildList {
            add("Плагин" to ownerLine(task.ownerName))
            add("Состояние" to TaskLabels.label(task.status))
            task.interval?.let { add("Повторяется" to "каждые ${TaskLabels.span(it)}") }
            TaskLabels.parent(task, data.tasks())?.let { add("Подзадача от" to it) }
            TaskLabels.children(task, data.tasks()).takeIf { it > 0 }?.let { add("Подзадач" to "$it") }
        }
        val about = task.description?.let { wrapText(it) } ?: listOf("Плагин не описал эту задачу.")
        builder.slot(
            4,
            icon(
                null, "CLOCK", TaskLabels.title(task), Style.lore(about, summary), alternative = "WATCH",
                danger = task.status == TaskStatus.FAILED, good = task.status == TaskStatus.RUNNING,
            ),
        )
    }

    private fun taskFacts(builder: MenuBuilder, task: TaskSnapshot) {
        val usage = task.usage?.let { wrapText(it) } ?: listOf("Не указано. Плагин может добавить usage в TaskSpec.")
        builder.slot(20, icon(Icons.CHANGELOG, "WRITTEN_BOOK", "Как используется", Style.lore(usage, emptyList()), muted = task.usage == null))
        val runs = buildList {
            add("Запусков" to "${task.runCount}")
            add("Пропущено" to "${task.skippedCount}")
            task.lastDuration?.let { add("Прошлый запуск шёл" to TaskLabels.span(it)) }
            TaskLabels.lag(task)?.let { add("Опоздание" to it) }
            add("Где" to TaskLabels.where(task))
        }
        builder.slot(21, icon(Icons.SETTINGS, "COMPARATOR", "Как выполняется", Style.lore(emptyList(), runs)))
        lagSlot(builder, task)
        taskTimes(builder, task)
    }

    private fun lagSlot(builder: MenuBuilder, task: TaskSnapshot) {
        val lag = TaskLabels.lag(task)
        val lore = buildList {
            task.lastLag?.let { add("Последнее" to TaskLabels.span(it)) }
            task.worstLag?.let { add("Наибольшее" to TaskLabels.span(it)) }
            add("Пропущено" to "${task.skippedCount}")
            if (task.failedCount > 0) add("Ошибок" to "${task.failedCount}")
        }
        val note = if (lag == null) "Запуски идут вовремя." else "Сервер или поток не успевали: запуск начинался позже срока."
        builder.slot(
            25,
            icon(Icons.PROBLEMS, "CLOCK", "Опоздания", Style.lore(listOf(note), lore), alternative = "WATCH", danger = lag != null, muted = lag == null),
        )
    }

    private fun taskTimes(builder: MenuBuilder, task: TaskSnapshot) {
        val times = buildList {
            add("Создана" to clockStamp(task.createdAt))
            task.nextRunAt?.let { add("Следующий запуск" to clockStamp(it)); add("" to ago(it)) }
            task.lastStartedAt?.let { add("Последний запуск" to clockStamp(it)); add("" to ago(it)) }
            task.lastCompletedAt?.let { add("Закончилась" to clockStamp(it)) }
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
            val lore = Style.lore(emptyList(), emptyList(), extra = wrapText(failure).map { Style.danger(it) })
            builder.slot(24, icon(Icons.PROBLEMS, "BARRIER", "Последняя ошибка", lore, danger = true))
        }
    }

    private fun runControl(builder: MenuBuilder, task: TaskSnapshot, back: (MenuViewer) -> Unit) {
        if (!(task.status == TaskStatus.SCHEDULED && task.runnableNow)) {
            val reason = TaskLabels.runAnswer(task).replaceFirstChar { it.uppercase() } + "."
            builder.slot(29, closedControl("Запуск закрыт", reason))
            return
        }
        val run = icon(
            Icons.CHECK, "EMERALD", "Запустить сейчас",
            Style.lore(listOf("Выполнить один раз, не дожидаясь срока. Расписание не меняется."), emptyList(), Style.left("запустить")),
            good = true,
        )
        builder.button(29, run, go { who -> allowed(who, TASKS) { runNow(who, task, back) } })
    }

    private fun runNow(who: MenuViewer, task: TaskSnapshot, back: (MenuViewer) -> Unit) {
        when (data.runTask(task.id.value)) {
            TaskControl.DONE -> tell(who, ChatTone.SUCCESS, "Задача запущена.")
            TaskControl.FORBIDDEN -> tell(who, ChatTone.FAILURE, "Плагин не разрешает запускать эту задачу вручную.")
            TaskControl.UNAVAILABLE -> tell(who, ChatTone.INFO, "Сейчас запустить нельзя: задача не ждёт срока.")
        }
        taskDetail(who, task.id.value, back)
    }

    private fun stopControl(builder: MenuBuilder, task: TaskSnapshot, back: (MenuViewer) -> Unit) {
        if (!task.isActive) {
            builder.slot(31, closedControl("Остановка закрыта", TaskLabels.stopAnswer(task).replaceFirstChar { it.uppercase() } + "."))
            return
        }
        if (!task.cancellable) {
            builder.slot(31, closedControl("Остановка закрыта", "Плагин запретил останавливать эту задачу из меню и команд."))
            return
        }
        val stop = icon(
            Icons.ROLLBACK, "TNT", "Остановить задачу",
            Style.lore(listOf("Больше не будет запускаться."), emptyList(), Style.left("остановить")), danger = true,
        )
        builder.button(31, stop, go { who -> allowed(who, TASKS) { stopNow(who, task, back) } })
    }

    private fun stopNow(who: MenuViewer, task: TaskSnapshot, back: (MenuViewer) -> Unit) {
        when (data.cancelTask(task.id.value)) {
            TaskControl.DONE -> tell(who, ChatTone.SUCCESS, "Задача остановлена.")
            TaskControl.FORBIDDEN -> tell(who, ChatTone.FAILURE, "Плагин не разрешает останавливать эту задачу.")
            TaskControl.UNAVAILABLE -> tell(who, ChatTone.INFO, "Задача уже не работает.")
        }
        taskDetail(who, task.id.value, back)
    }

    private fun closedControl(name: String, reason: String) =
        icon(Icons.PROBLEMS, "BARRIER", name, Style.lore(listOf(reason), emptyList()), muted = true)

    /** What the event called [name] says about itself, or null when it is unknown. */
    fun aboutOf(name: String): EventAbout? = data.eventAbout(name)

    /** `Name 1.2.3` for a plugin, or the plain [name] when the server does not know it. */
    fun ownerLine(name: String): String = data.ownerNamed(name)?.let { versioned(it) } ?: name

    fun versioned(owner: OwnerInfo): String = listOfNotNull(owner.name, owner.version).joinToString(" ")

    fun ownerParameters(owner: OwnerInfo?): List<Pair<String, String>> = owner?.let {
        buildList {
            add("Плагин" to it.name)
            it.version?.let { version -> add("Версия" to version) }
            if (it.authors.isNotEmpty()) add("Автор" to it.authors.joinToString(", "))
        }
    } ?: listOf("Плагин" to "сервер или библиотека")

    /** [text] as `[ label ]` followed by indented lines, for a value that does not fit one line. */
    fun labelled(label: String, text: String): List<Pair<String, String>> =
        wrapText(text).mapIndexed { index, line -> (if (index == 0) label else "") to line }
}
