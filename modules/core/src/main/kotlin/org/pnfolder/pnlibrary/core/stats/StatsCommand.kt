package org.pnfolder.pnlibrary.core.stats

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.command
import org.pnfolder.pnlibrary.core.journal.ago
import org.pnfolder.pnlibrary.core.stats.StatsSnapshot

/**
 * `/pn stats`: the numbers of the server right now (tasks, events, warnings and errors of the day) and how they
 * looked at the last few hourly snapshots kept in the journal. Requires `pnlibrary.stats`; the console always may.
 */
internal fun statsCommand(collector: StatsCollector, recorder: StatsRecorder): CommandDefinition = command("stats") {
    permission(PERMISSION)
    noPermissionMessage("Статистика доступна администраторам. Нужно право $PERMISSION.")
    consoleBypassesPermission()
    executes { context -> statsLines(collector.collect(), recorder.history(HISTORY_DAYS, HISTORY_ROWS)).forEach(context.sender::send) }
}

private const val PERMISSION = "pnlibrary.stats"
private const val HISTORY_DAYS = 7L
private const val HISTORY_ROWS = 6

/** The chat lines of the statistics card; also used by the menu. */
internal fun statsLines(now: StatsSnapshot, history: List<StatsSnapshot>): List<Component> {
    val lines = mutableListOf<Component>()
    lines += Component.text("Статистика pnLibrary", ChatPalette.ACCENT)
    lines += row("Задачи", "${now.tasksActive} активно, ${now.tasksFailed} с ошибкой", if (now.tasksFailed > 0) ChatPalette.WARN else ChatPalette.OK)
    lines += row("Запуски задач", "${now.taskRuns}, пропущено ${now.taskSkipped}", ChatPalette.SOFT)
    lines += row("События", "${now.eventsCalled} вызовов, отменено ${now.eventsDenied}", ChatPalette.SOFT)
    lines += row("За сутки", "${now.errors24h} ошибок, ${now.warnings24h} предупреждений", if (now.errors24h > 0) ChatPalette.DANGER else ChatPalette.OK)
    if (now.errorSources.isNotEmpty()) {
        lines += row("Больше всего ошибок", now.errorSources.joinToString { (source, count) -> "$source ($count)" }, ChatPalette.WARN)
    }
    if (history.isNotEmpty()) {
        lines += Component.text("Прошлые срезы", ChatPalette.LABEL)
        history.forEach { lines += row(ago(it.timeMillis), "${it.tasksActive} задач, ${it.errors24h} ошибок", ChatPalette.FAINT) }
    }
    return lines
}

private fun row(label: String, value: String, color: TextColor): Component =
    Component.text("$label: ", ChatPalette.SOFT).append(Component.text(value, color))
