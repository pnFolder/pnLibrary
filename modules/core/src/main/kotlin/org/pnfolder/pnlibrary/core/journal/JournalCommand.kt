package org.pnfolder.pnlibrary.core.journal

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.pnfolder.pnlibrary.api.chat.ChatKit
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.chat.ChatReader
import org.pnfolder.pnlibrary.api.commands.ArgumentType
import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.CommandSender
import org.pnfolder.pnlibrary.api.commands.command
import org.pnfolder.pnlibrary.common.text.RussianText
import org.pnfolder.pnlibrary.core.runtime.DisplayZone
import java.util.Locale

/** rows shown on one page of `/pn events`. */
private const val PAGE_SIZE = 5

/** Longest message shown in a player's chat so lines do not flood it; the console and the card show all. */
private const val CHAT_CHARS = 46

/** Records read to fold repeats and to count how often an event happened. */
private const val SCAN_LIMIT = 5_000

/** Width in characters at which hover text is wrapped, and how many message lines a hover shows. */
private const val HOVER_WIDTH = 44
private const val HOVER_MESSAGE_LINES = 4

/** Width at which the message in the card is wrapped. */
private const val CARD_WIDTH = 56

/** Occurrence times listed on the repeats page. */
private const val REPEAT_ROWS = 15

/**
 * `/pn events [info|warning|error|all] [page]`: the journal with repeats of one event folded into a single row.
 *
 * A row shows the last time, level, source, how many times it happened and a message that is shortened in
 * player chat and whole in the console. Hovering shows a summary; clicking opens the card
 * `/pn events view <id>` with the full text, first and last time and the stack trace, plus buttons for the
 * repeats (`/pn events times <id>`) and for copying a report to hand to the plugin author.
 * Requires `pnlibrary.events.view`; the console always may read it.
 */
internal fun journalCommand(journal: JournalStore, root: String = "/pn events"): CommandDefinition = command("events") {
    permission("pnlibrary.events.view")
    noPermissionMessage("Журнал событий доступен администраторам. Нужно право pnlibrary.events.view.")
    consoleBypassesPermission()
    // The router accepts only declared words, so every level, page and card id is declared here.
    executes { context -> list(context.sender, ChatKit(ChatReader.of(context.sender)), journal, context.arguments, root) }
    listOf("all", "info", "warning", "error").forEach { level ->
        literal(level) {
            executes { context -> list(context.sender, ChatKit(ChatReader.of(context.sender)), journal, context.arguments, root) }
            argument("page", ArgumentType.integer()) {
                executes { context -> list(context.sender, ChatKit(ChatReader.of(context.sender)), journal, context.arguments, root) }
            }
        }
    }
    literal("view") {
        argument("id", ArgumentType.string()) {
            executes { context ->
                card(context.sender, ChatKit(ChatReader.of(context.sender)), journal, context.arguments.getOrNull(1), repeats = false, root = root)
            }
        }
    }
    literal("full") {
        argument("id", ArgumentType.string()) {
            executes { context ->
                card(context.sender, ChatKit(ChatReader.of(context.sender)), journal, context.arguments.getOrNull(1), repeats = false, fullTrace = true, root = root)
            }
        }
    }
    literal("times") {
        argument("id", ArgumentType.string()) {
            executes { context ->
                card(context.sender, ChatKit(ChatReader.of(context.sender)), journal, context.arguments.getOrNull(1), repeats = true, root = root)
            }
        }
    }
}

/** Splits [text] into lines of at most [width] characters at spaces. */
private fun wrap(text: String, width: Int): List<String> {
    val lines = mutableListOf<String>()
    var current = StringBuilder()
    text.split(' ').forEach { word ->
        if (current.isNotEmpty() && current.length + 1 + word.length > width) {
            lines += current.toString()
            current = StringBuilder()
        }
        if (current.isNotEmpty()) current.append(' ')
        current.append(word)
    }
    if (current.isNotEmpty()) lines += current.toString()
    return lines
}

private fun levelStyle(level: JournalLevel) = when (level) {
    JournalLevel.ERROR -> "ОШИБКА" to ChatPalette.DANGER
    JournalLevel.WARNING -> "ВНИМАНИЕ" to ChatPalette.WARN
    JournalLevel.INFO -> "инфо" to ChatPalette.SOFT
}

/** A `|` in the level's color; only the first line of a row has it underlined (`&n|`), the second one is plain. */
private fun levelBar(kit: ChatKit, level: JournalLevel, underlined: Boolean): Component =
    kit.text("|", levelStyle(level).second).decoration(net.kyori.adventure.text.format.TextDecoration.UNDERLINED, underlined)

/** The level's own shape and color: a disc for an error, a triangle for a warning, a diamond for information. */
private fun levelIcon(kit: ChatKit, level: JournalLevel): Component = when (level) {
    JournalLevel.ERROR -> kit.text(kit.glyph('●'), ChatPalette.DANGER)
    JournalLevel.WARNING -> kit.text(kit.glyph('▲'), ChatPalette.WARN)
    JournalLevel.INFO -> kit.text(kit.glyph('◆'), ChatPalette.INFO)
}

/** Filter words in the order of the tabs: the command word, the tab label and the levels it shows. */
private val FILTERS = listOf(
    Triple("all", "все", emptySet<JournalLevel>()),
    Triple("error", "ошибки", setOf(JournalLevel.ERROR)),
    Triple("warning", "предупр.", setOf(JournalLevel.WARNING)),
    Triple("info", "инфо", setOf(JournalLevel.INFO)),
)

private fun list(sender: CommandSender, kit: ChatKit, journal: JournalStore, arguments: List<String>, root: String) {
    val word = arguments.getOrNull(0)?.lowercase(Locale.ROOT) ?: "all"
    val filter = FILTERS.firstOrNull { it.first == word || (word == "warn" && it.first == "warning") }
    if (filter == null) {
        sender.send(kit.text("Неизвестный уровень $word. Доступны: all, error, warning, info.", ChatPalette.DANGER))
        return
    }
    val everything = journal.read(limit = SCAN_LIMIT).groupBy { it.fingerprint }.values
        .map { it.sortedByDescending(JournalRecord::timeMillis) }
        .sortedByDescending { it.first().timeMillis }
    val groups = if (filter.third.isEmpty()) everything else everything.filter { it.first().level in filter.third }
    val pages = ((groups.size + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
    val page = (arguments.getOrNull(1)?.toIntOrNull() ?: 1).coerceIn(1, pages)
    val rows = groups.drop((page - 1) * PAGE_SIZE).take(PAGE_SIZE)

    val lines = mutableListOf<Component>(
        kit.blank(),
        kit.blank(),
        kit.header("События", kit.text("${groups.size} ${RussianText.plural(groups.size.toLong(), "запись", "записи", "записей")}", ChatPalette.LABEL)),
        tabs(kit, everything, filter.first, root),
        kit.blank(),
    )
    if (rows.isEmpty()) lines += kit.note("Здесь пока пусто.")
    rows.forEach { group ->
        lines += row(kit, group, sender.isConsole, root)
        lines += kit.blank()
    }
    if (pages > 1) {
        lines += pager(kit, filter.first, page, pages, root)
    }
    if (!kit.reader.interactive) {
        lines += kit.blank()
        lines += kit.note("Уровни: $root all | error | warning | info [страница]; карточка: $root view <id>")
    }
    lines.forEach(sender::send)
}

/**
 * `Показать:` and one button per filter, such as `[ошибки 3]`. Every button has the same gray brackets; the open
 * filter is violet and the others are gray, so the difference is one color and never depends on the level.
 */
private fun tabs(kit: ChatKit, everything: List<List<JournalRecord>>, current: String, root: String): Component {
    var line = kit.text(" Показать: ", ChatPalette.LABEL)
    FILTERS.forEachIndexed { index, (word, label, levels) ->
        if (index > 0) line = line.append(kit.text(" "))
        val count = if (levels.isEmpty()) everything.size else everything.count { it.first().level in levels }
        val open = word == current
        val body = if (open) ChatPalette.ACCENT else if (count == 0) ChatPalette.FAINT else ChatPalette.SOFT
        val button = kit.text("[", ChatPalette.FAINT).append(kit.text("$label $count", body)).append(kit.text("]", ChatPalette.FAINT))
        line = line.append(
            if (open || !kit.reader.interactive) button
            else button.clickEvent(ClickEvent.runCommand("$root $word"))
                .hoverEvent(kit.tooltip(kit.text("Нажмите, чтобы показать: $label", ChatPalette.ACCENT), kit.blank(), kit.text("$root $word", ChatPalette.LABEL))),
        )
    }
    return line
}

/** `« 1 … 4 [5] 6 … 12 »`: arrows and a window of page numbers, so a long journal is never one wall of rows. */
private fun pager(kit: ChatKit, word: String, page: Int, pages: Int, root: String): Component {
    fun link(target: Int, label: String): Component =
        kit.option(label, "$root $word $target", "Страница $target из $pages", ChatPalette.SOFT)
    val shown = (listOf(1, pages) + (page - 1..page + 1)).filter { it in 1..pages }.distinct().sorted()
    var line = kit.text("  ")
    line = line.append(if (page > 1) link(page - 1, "‹ назад") else kit.text("[‹ назад]", ChatPalette.FAINT))
    var previous = 0
    shown.forEach { number ->
        if (number - previous > 1) line = line.append(kit.text(" …", ChatPalette.FAINT))
        line = line.append(kit.text(" ")).append(
            if (number == page) kit.text("$number", ChatPalette.ACCENT).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD)
            else link(number, "$number"),
        )
        previous = number
    }
    line = line.append(kit.text(" ")).append(if (page < pages) link(page + 1, "дальше ›") else kit.text("[дальше ›]", ChatPalette.FAINT))
    return line.append(kit.text("   страница $page из $pages", ChatPalette.FAINT))
}

/**
 * One event as a two-line block with a colored bar on its left edge: the source, the repeat count and how long
 * ago on the first line, the message on the second. [group] holds the repeats, newest first.
 */
private fun row(kit: ChatKit, group: List<JournalRecord>, full: Boolean, root: String): Component {
    val record = group.first()
    val (tag, color) = levelStyle(record.level)
    val text = if (!full && record.message.length > CHAT_CHARS) record.message.take(CHAT_CHARS) + "…" else record.message
    val firstBar = levelBar(kit, record.level, underlined = true)
    val secondBar = levelBar(kit, record.level, underlined = false)
    val icon = levelIcon(kit, record.level)
    val hover = mutableListOf<Component>(
        icon.append(kit.text(" $tag", color)).append(kit.dot()).append(kit.text(record.source, ChatPalette.ACCENT)),
        kit.blank(),
    )
    wrap(record.message.lineSequence().first(), HOVER_WIDTH).take(HOVER_MESSAGE_LINES).forEach { hover += kit.text(it, ChatPalette.TEXT) }
    hover += kit.blank()
    hover += kit.hoverField("Последний раз", kit.text(fullStamp(record.timeMillis), ChatPalette.TEXT))
    if (group.size > 1) {
        hover += kit.hoverField("Первый раз", kit.text(fullStamp(group.last().timeMillis), ChatPalette.TEXT))
        hover += kit.hoverField("Повторов", kit.text(group.size.toString(), ChatPalette.ORANGE))
    }
    hover += kit.blank()
    hover += kit.text("Нажмите, чтобы открыть карточку ›", ChatPalette.ACCENT)
    var first = kit.text(" ").append(firstBar).append(kit.text(" ")).append(icon).append(kit.text(" "))
        .append(kit.text(record.source, ChatPalette.ACCENT))
    if (group.size > 1) first = first.append(kit.text("  ×${group.size}", ChatPalette.ORANGE))
    first = first.append(kit.text("  ${ago(record.timeMillis)}", agoColor(record.timeMillis)))
    val second = kit.text(" ").append(secondBar).append(kit.text("   $text", ChatPalette.TEXT))
    return first.append(Component.newline()).append(second)
        .hoverEvent(kit.tooltip(*hover.toTypedArray()))
        .clickEvent(ClickEvent.runCommand("$root view ${record.fingerprint}"))
}

/** The detail card of one event, or the timeline of its occurrences when [repeats] is set. */
private fun card(
    sender: CommandSender,
    kit: ChatKit,
    journal: JournalStore,
    id: String?,
    repeats: Boolean,
    fullTrace: Boolean = false,
    root: String,
) {
    val group = journal.read(limit = SCAN_LIMIT).filter { it.fingerprint == id }.sortedByDescending { it.timeMillis }
    if (id == null || group.isEmpty()) {
        sender.send(kit.text("Такой записи нет: возможно, её уже удалили по сроку хранения.", ChatPalette.DANGER))
        return
    }
    val record = group.first()
    val (tag, color) = levelStyle(record.level)
    var traceHidden = false
    val lines = mutableListOf<Component>(
        kit.blank(),
        kit.blank(),
        kit.header("Событие $id", kit.bracketed(tag, color)),
        kit.text("  ${record.source}", ChatPalette.ACCENT).append(kit.dot()).append(kit.text(record.type, ChatPalette.LABEL)),
        kit.blank(),
    )
    lines += kit.section("Когда")
    lines += kit.field(
        "Последний раз",
        moment(kit, record.timeMillis).append(kit.text("  ${ago(record.timeMillis)}", agoColor(record.timeMillis))),
    )
    lines += kit.field("Часовой пояс", kit.text(zoneLabel(), ChatPalette.SOFT))
    if (group.size > 1) {
        val gap = (group.first().timeMillis - group.last().timeMillis) / (group.size - 1)
        lines += kit.field("Первый раз", moment(kit, group.last().timeMillis).append(kit.text("  ${ago(group.last().timeMillis)}", agoColor(group.last().timeMillis))))
        lines += kit.field(
            "Повторов",
            kit.text(group.size.toString(), ChatPalette.ORANGE).append(kit.text("  примерно раз в ${span(gap)}", ChatPalette.SOFT)),
        )
    }
    lines += kit.blank()
    if (repeats) {
        val zone = DisplayZone.zone
        val today = java.time.LocalDate.now(zone)
        val days = group.take(REPEAT_ROWS).groupBy { java.time.Instant.ofEpochMilli(it.timeMillis).atZone(zone).toLocalDate() }
        days.forEach { (day, items) ->
            val title = when (day) {
                today -> "Сегодня"
                today.minusDays(1) -> "Вчера"
                else -> longDate(day)
            }
            val dayTip = kit.tooltip(
                kit.text(DAY_WITH_YEAR.format(day), ChatPalette.TEXT),
                kit.text(WEEKDAY.format(day), ChatPalette.LABEL),
                kit.blank(),
                kit.hoverField("Событий в этот день", kit.text(items.size.toString(), ChatPalette.ORANGE)),
            )
            var heading = kit.section(title)
            if (day == today || day == today.minusDays(1)) heading = heading.append(kit.text(" · ${longDate(day)}", ChatPalette.SOFT))
            lines += heading.append(kit.text("  ×${items.size}", ChatPalette.ORANGE)).hoverEvent(dayTip)
            items.forEach { item ->
                lines += kit.text("    " + CLOCK.withZone(zone).format(java.time.Instant.ofEpochMilli(item.timeMillis)), ChatPalette.TEXT)
                    .append(kit.text("  ${ago(item.timeMillis)}", agoColor(item.timeMillis)))
                    .hoverEvent(momentTip(kit, item.timeMillis))
            }
            lines += kit.blank()
        }
        if (group.size > REPEAT_ROWS) {
            lines += kit.note("Показаны последние $REPEAT_ROWS из ${group.size}.")
            lines += kit.blank()
        }
    } else {
        lines += kit.section("Что случилось")
        wrap(record.message, CARD_WIDTH).forEach { lines += kit.text("    $it", ChatPalette.TEXT) }
        record.details.filterKeys { it != JournalRecord.STACKTRACE }.forEach { (key, value) ->
            lines += kit.text("    $key: ", ChatPalette.LABEL).append(kit.text(value, ChatPalette.SOFT))
        }
        val trace = record.details[JournalRecord.STACKTRACE]?.lines().orEmpty()
        if (trace.isNotEmpty()) {
            lines += kit.blank()
            lines += kit.section("Стек вызовов")
            val (view, hidden) = traceView(kit, trace, sender.isConsole || fullTrace)
            lines += view
            traceHidden = hidden
        }
        lines += kit.blank()
    }
    val buttons = mutableListOf<Component?>(
        kit.button("назад", "$root", "К списку событий", ChatPalette.ACCENT),
        if (repeats) kit.button("карточка", "$root view $id", "Вернуться к карточке", ChatPalette.ACCENT)
        else if (group.size > 1) kit.button("повторы", "$root times $id", "Когда это повторялось", ChatPalette.ACCENT)
        else null,
        if (traceHidden) kit.button("показать всё", "$root full $id", "Стек вызовов целиком, ничего не свёрнуто", ChatPalette.ACCENT)
        else if (fullTrace) kit.button("свернуть", "$root view $id", "Снова свернуть служебные вызовы", ChatPalette.ACCENT)
        else null,
    )
    if (kit.reader.interactive) {
        buttons += kit.bracketed("скопировать", ChatPalette.OK)
            .clickEvent(ClickEvent.copyToClipboard(report(record, group.size)))
            .hoverEvent(
                kit.tooltip(
                    kit.text("Скопировать отчёт для автора плагина", ChatPalette.OK),
                    kit.blank(),
                    kit.text("Время, число повторов, сообщение и весь стек вызовов", ChatPalette.LABEL),
                ),
            )
    }
    lines += kit.actions(*buttons.toTypedArray()) ?: kit.blank()
    lines.forEach(sender::send)
}

/** Plain text for the plugin author: what happened, how often, and the whole stack trace. */
private fun report(record: JournalRecord, count: Int): String = buildString {
    appendLine("[${record.level}] ${record.source} · ${record.type}")
    appendLine("Время: ${stamp(record.timeMillis)} ${zoneLabel()}, повторов: $count")
    appendLine(record.message)
    record.details.filterKeys { it != JournalRecord.STACKTRACE }.forEach { (key, value) -> appendLine("$key: $value") }
    record.details[JournalRecord.STACKTRACE]?.let {
        appendLine()
        append(it)
    }
}.trim()
