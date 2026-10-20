package org.pnfolder.pnlibrary.core.journal

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.chat.ChatKit
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.chat.ChatReader
import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.command
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Records shown on one page of `/pnevents`. */
private const val PAGE_SIZE = 8

/** Longest message shown in the list; the full text is in the hover card. */
private const val LIST_CHARS = 90

/**
 * `/pnevents [info|warning|error|all] [page]`: the last records of the journal, newest first.
 *
 * Each row shows the time, level, source and a short message. Hovering over a row shows the full
 * message and details. Page buttons repeat the same filter, so one click moves through the history.
 * Requires `pnlibrary.events.view`; the console always may read it.
 */
internal fun journalCommand(journal: JournalStore): CommandDefinition = command("pnevents") {
    aliases("pnevent")
    permission("pnlibrary.events.view")
    noPermissionMessage("Журнал событий доступен администраторам. Нужно право pnlibrary.events.view.")
    consoleBypassesPermission()
    executes { context -> show(context.sender, journal, context.arguments) }
}

private fun show(sender: org.pnfolder.pnlibrary.api.commands.CommandSender, journal: JournalStore, arguments: List<String>) {
    val kit = ChatKit(ChatReader.of(sender))
    val filter = arguments.getOrNull(0)?.lowercase(Locale.ROOT)
    val levels = when (filter) {
        null, "all", "все" -> emptySet()
        "info", "инфо" -> setOf(JournalLevel.INFO)
        "warning", "warn", "предупреждения" -> setOf(JournalLevel.WARNING)
        "error", "ошибки" -> setOf(JournalLevel.ERROR)
        else -> {
            sender.send(kit.text("Неизвестный уровень $filter. Доступны: info, warning, error, all.", ChatPalette.DANGER))
            return
        }
    }
    val page = arguments.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val all = journal.read(levels = levels, limit = page * PAGE_SIZE + 1)
    val pageRows = all.drop((page - 1) * PAGE_SIZE).take(PAGE_SIZE)
    val hasNext = all.size > page * PAGE_SIZE

    val lines = mutableListOf<Component>(
        kit.header("События", kit.text("страница $page", ChatPalette.LABEL)),
        kit.blank(),
    )
    if (pageRows.isEmpty()) {
        lines += kit.note("Записей нет.")
    }
    pageRows.forEach { record -> lines += row(kit, record) }
    lines += kit.blank()
    lines += kit.actions(
        if (page > 1) kit.button("[назад]", "/pnevents ${filter ?: "all"} ${page - 1}", "Предыдущая страница", ChatPalette.ACCENT) else null,
        if (hasNext) kit.button("[дальше]", "/pnevents ${filter ?: "all"} ${page + 1}", "Следующая страница", ChatPalette.ACCENT) else null,
    ) ?: kit.note("Конец журнала.")
    lines.forEach(sender::send)
}

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM HH:mm:ss").withZone(ZoneId.systemDefault())

private fun row(kit: ChatKit, record: JournalRecord): Component {
    val (tag, color) = when (record.level) {
        JournalLevel.ERROR -> "ОШИБКА" to ChatPalette.DANGER
        JournalLevel.WARNING -> "ВНИМАНИЕ" to ChatPalette.WARN
        JournalLevel.INFO -> "инфо" to ChatPalette.SOFT
    }
    val short = if (record.message.length > LIST_CHARS) record.message.take(LIST_CHARS) + "…" else record.message
    val hover = kit.text("${record.source} · ${record.type}", ChatPalette.ACCENT).append(Component.newline())
        .append(kit.text(record.message.take(JournalRecord.MAX_MESSAGE_CHARS), ChatPalette.TEXT))
        .also { record.details.forEach { (key, value) -> it.append(Component.newline()).append(kit.text("$key: $value", ChatPalette.LABEL)) } }
    return kit.text(TIME.format(java.time.Instant.ofEpochMilli(record.timeMillis)) + "  ", ChatPalette.LABEL)
        .append(kit.text("[$tag] ", color))
        .append(kit.text("${record.source}: ", ChatPalette.ACCENT))
        .append(kit.text(short, ChatPalette.TEXT))
        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(hover))
}
