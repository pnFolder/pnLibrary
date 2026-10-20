package org.pnfolder.pnlibrary.core.journal

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.TextColor
import org.pnfolder.pnlibrary.api.chat.ChatKit
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.chat.ChatReader
import org.pnfolder.pnlibrary.api.commands.ArgumentType
import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.CommandSender
import org.pnfolder.pnlibrary.api.commands.command
import org.pnfolder.pnlibrary.core.runtime.DisplayZone
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Rows shown on one page of `/pnevents`. */
private const val PAGE_SIZE = 5

/** Longest message shown in a player's chat so lines do not flood it; the console and the card show all. */
private const val CHAT_CHARS = 46

/** Stack trace lines in a player's chat card; the console prints all of them. */
private const val CARD_TRACE_LINES = 14

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
 * `/pnevents [info|warning|error|all] [page]`: the journal with repeats of one event folded into a single row.
 *
 * A row shows the last time, level, source, how many times it happened and a message that is shortened in
 * player chat and whole in the console. Hovering shows a summary; clicking opens the card
 * `/pnevents view <id>` with the full text, first and last time and the stack trace, plus buttons for the
 * repeats (`/pnevents times <id>`) and for copying a report to hand to the plugin author.
 * Requires `pnlibrary.events.view`; the console always may read it.
 */
internal fun journalCommand(journal: JournalStore): CommandDefinition = command("pnevents") {
    aliases("pnevent")
    permission("pnlibrary.events.view")
    noPermissionMessage("Журнал событий доступен администраторам. Нужно право pnlibrary.events.view.")
    consoleBypassesPermission()
    // The router accepts only declared words, so every level, page and card id is declared here.
    executes { context -> list(context.sender, ChatKit(ChatReader.of(context.sender)), journal, context.arguments) }
    listOf("all", "info", "warning", "error").forEach { level ->
        literal(level) {
            executes { context -> list(context.sender, ChatKit(ChatReader.of(context.sender)), journal, context.arguments) }
            argument("page", ArgumentType.integer()) {
                executes { context -> list(context.sender, ChatKit(ChatReader.of(context.sender)), journal, context.arguments) }
            }
        }
    }
    literal("view") {
        argument("id", ArgumentType.string()) {
            executes { context ->
                card(context.sender, ChatKit(ChatReader.of(context.sender)), journal, context.arguments.getOrNull(1), repeats = false)
            }
        }
    }
    literal("full") {
        argument("id", ArgumentType.string()) {
            executes { context ->
                card(context.sender, ChatKit(ChatReader.of(context.sender)), journal, context.arguments.getOrNull(1), repeats = false, fullTrace = true)
            }
        }
    }
    literal("times") {
        argument("id", ArgumentType.string()) {
            executes { context ->
                card(context.sender, ChatKit(ChatReader.of(context.sender)), journal, context.arguments.getOrNull(1), repeats = true)
            }
        }
    }
}

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM HH:mm:ss")

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

private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM")
private val DAY_WITH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

private val TIME_WITH_YEAR: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")

/** Day and time, with the year only when it is not the current one, so old events are never ambiguous. */
private fun stamp(millis: Long): String {
    val instant = java.time.Instant.ofEpochMilli(millis)
    val zone = DisplayZone.zone
    return if (instant.atZone(zone).year == java.time.LocalDate.now(zone).year) TIME.withZone(zone).format(instant) else TIME_WITH_YEAR.withZone(zone).format(instant)
}

/** Russian plural form for [count]: one, few (2-4) or many. */
internal fun plural(count: Long, one: String, few: String, many: String): String = when {
    count % 100 in 11..14 -> many
    count % 10 == 1L -> one
    count % 10 in 2..4 -> few
    else -> many
}

private val RU = Locale("ru")
private val LONG_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", RU)
private val LONG_DAY_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy 'года'", RU)
private val WEEKDAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE", RU)
private val UTC_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(java.time.ZoneOffset.UTC)

/** "16 октября 2023 года", or "16 октября" within the current year. */
private fun longDate(date: java.time.LocalDate): String =
    if (date.year == java.time.LocalDate.now(DisplayZone.zone).year) LONG_DAY.format(date) else LONG_DAY_YEAR.format(date)

private fun localDate(millis: Long): java.time.LocalDate = java.time.Instant.ofEpochMilli(millis).atZone(DisplayZone.zone).toLocalDate()

/** "16 октября 2023 года, 16:45:07": the date in words, then the clock in the display zone. */
private fun fullStamp(millis: Long): String =
    "${longDate(localDate(millis))}, ${CLOCK.withZone(DisplayZone.zone).format(java.time.Instant.ofEpochMilli(millis))}"

/** The same moment as numbers and in UTC, for the hover card, so the exact value is always one hover away. */
private fun momentTip(kit: ChatKit, millis: Long) = kit.tooltip(
    kit.text(TIME_WITH_YEAR.withZone(DisplayZone.zone).format(java.time.Instant.ofEpochMilli(millis)), ChatPalette.TEXT),
    kit.text(WEEKDAY.format(localDate(millis)), ChatPalette.LABEL),
    kit.blank(),
    kit.hoverField("UTC", kit.text(UTC_STAMP.format(java.time.Instant.ofEpochMilli(millis)), ChatPalette.TEXT)),
    kit.hoverField("Часовой пояс", kit.text(zoneLabel(), ChatPalette.TEXT)),
    kit.hoverField("Прошло", kit.text(ago(millis).removeSuffix(" назад"), agoColor(millis))),
)

/** A date and time in words that shows the exact numbers when hovered. */
private fun moment(kit: ChatKit, millis: Long): Component =
    kit.text(fullStamp(millis), ChatPalette.TEXT).hoverEvent(momentTip(kit, millis))

/** "How long ago": gray, and dimmer once it is more than a month old. */
private fun agoColor(millis: Long, now: Long = System.currentTimeMillis()): TextColor =
    if (now - millis < 30L * 86_400_000) ChatPalette.SOFT else ChatPalette.FAINT

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

private fun list(sender: CommandSender, kit: ChatKit, journal: JournalStore, arguments: List<String>) {
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
        kit.header("События", kit.text("${groups.size} ${plural(groups.size.toLong(), "запись", "записи", "записей")}", ChatPalette.LABEL)),
        tabs(kit, everything, filter.first),
        kit.blank(),
    )
    if (rows.isEmpty()) lines += kit.note("Здесь пока пусто.")
    rows.forEach { group ->
        lines += row(kit, group, sender.isConsole)
        lines += kit.blank()
    }
    if (pages > 1) {
        lines += pager(kit, filter.first, page, pages)
    }
    if (!kit.reader.interactive) {
        lines += kit.blank()
        lines += kit.note("Уровни: /pnevents all | error | warning | info [страница]; карточка: /pnevents view <id>")
    }
    lines.forEach(sender::send)
}

/**
 * `Показать:` and one button per filter, such as `[ошибки 3]`. Every button has the same gray brackets; the open
 * filter is violet and the others are gray, so the difference is one color and never depends on the level.
 */
private fun tabs(kit: ChatKit, everything: List<List<JournalRecord>>, current: String): Component {
    var line = kit.text(" Показать: ", ChatPalette.LABEL)
    FILTERS.forEachIndexed { index, (word, label, levels) ->
        if (index > 0) line = line.append(kit.text(" "))
        val count = if (levels.isEmpty()) everything.size else everything.count { it.first().level in levels }
        val open = word == current
        val body = if (open) ChatPalette.ACCENT else if (count == 0) ChatPalette.FAINT else ChatPalette.SOFT
        val button = kit.text("[", ChatPalette.FAINT).append(kit.text("$label $count", body)).append(kit.text("]", ChatPalette.FAINT))
        line = line.append(
            if (open || !kit.reader.interactive) button
            else button.clickEvent(ClickEvent.runCommand("/pnevents $word"))
                .hoverEvent(kit.tooltip(kit.text("Нажмите, чтобы показать: $label", ChatPalette.ACCENT), kit.blank(), kit.text("/pnevents $word", ChatPalette.LABEL))),
        )
    }
    return line
}

/** `« 1 … 4 [5] 6 … 12 »`: arrows and a window of page numbers, so a long journal is never one wall of rows. */
private fun pager(kit: ChatKit, word: String, page: Int, pages: Int): Component {
    fun link(target: Int, label: String): Component =
        kit.option(label, "/pnevents $word $target", "Страница $target из $pages", ChatPalette.SOFT)
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
private fun row(kit: ChatKit, group: List<JournalRecord>, full: Boolean): Component {
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
        .clickEvent(ClickEvent.runCommand("/pnevents view ${record.fingerprint}"))
}

/**
 * How long ago [millis] was, with the two largest units: "только что", "5 мин назад", "3 ч 12 мин назад",
 * "5 дней назад", "3 месяца 2 дня назад" or "3 года 1 день назад".
 */
internal fun ago(millis: Long, now: Long = System.currentTimeMillis(), zone: java.time.ZoneId = DisplayZone.zone): String {
    val seconds = ((now - millis) / 1000).coerceAtLeast(0)
    if (seconds < 60) return "только что"
    if (seconds < 86_400) {
        val hours = seconds / 3600
        val minutes = seconds % 3600 / 60
        return (if (hours == 0L) "$minutes мин" else if (minutes == 0L) "$hours ч" else "$hours ч $minutes мин") + " назад"
    }
    val from = java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val to = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val period = java.time.Period.between(from, to)
    val parts = listOf(
        period.years.toLong() to Triple("год", "года", "лет"),
        period.months.toLong() to Triple("месяц", "месяца", "месяцев"),
        period.days.toLong() to Triple("день", "дня", "дней"),
    ).filter { it.first > 0 }.take(2).map { (count, forms) -> "$count ${plural(count, forms.first, forms.second, forms.third)}" }
    return (parts.joinToString(" ").ifEmpty { "1 день" }) + " назад"
}

/** A span such as "1 ч 20 мин" for the average gap between repeats. */
private fun span(millis: Long): String {
    val minutes = millis / 60_000
    return when {
        minutes < 1 -> "меньше минуты"
        minutes < 60 -> "$minutes мин"
        minutes < 1440 -> "${minutes / 60} ч ${minutes % 60} мин"
        else -> "${minutes / 1440} дн ${minutes % 1440 / 60} ч"
    }
}

/** Stack trace frame: optional module prefix, class, method and the `File.java:44` location. */
private val FRAME = Regex("""^at\s+(?:\S*/)?([\w$.]+)\.([\w$<>]+)\((.*)\)$""")

/** Exception line: optional `Caused by: `, the class and its message. */
private val HEAD = Regex("""^(Caused by: |Suppressed: )?([\w$.]+)(?::\s?(.*))?$""")

/** Packages of the JDK, the server and common libraries: their frames are dimmed, the plugin's own stay bright. */
private val FRAMEWORK = listOf(
    "java.", "javax.", "jdk.", "sun.", "org.bukkit.", "io.papermc.", "net.minecraft.", "com.mojang.", "org.spigotmc.",
    "net.md_5.", "com.destroystokyo.", "io.netty.", "org.apache.", "kotlin.", "kotlinx.", "com.velocitypowered.",
    "net.kyori.", "org.jetbrains.",
)


/** One trace line as colored text, and whether it is a frame of the JDK, the server or a library. */
private fun traceLine(kit: ChatKit, line: String): Pair<Component, Boolean> {
    val text = line.replace("\t", " ").trim()
    FRAME.matchEntire(text)?.let { match ->
        val (className, method, location) = match.destructured
        val framework = FRAMEWORK.any { className.startsWith(it) }
        val tip = kit.tooltip(
            kit.text("$className.$method", if (framework) ChatPalette.FAINT else ChatPalette.TEXT),
            kit.text(location, if (framework) ChatPalette.FAINT else ChatPalette.SOFT),
        )
        val component = if (framework) {
            kit.text("    · ", ChatPalette.FAINT)
                .append(kit.text("${className.substringAfterLast('.')}.$method", ChatPalette.FAINT))
                .append(kit.text("  $location", ChatPalette.FAINT))
        } else {
            kit.text("    ▸ ", ChatPalette.ORANGE)
                .append(kit.text(className.substringAfterLast('.'), ChatPalette.TEXT))
                .append(kit.text(".", ChatPalette.FAINT))
                .append(kit.text(method, ChatPalette.ACCENT))
                .append(kit.text("  $location", ChatPalette.SOFT))
        }
        return component.hoverEvent(tip) to framework
    }
    HEAD.matchEntire(text)?.let { match ->
        val prefix = match.groupValues[1]
        val className = match.groupValues[2]
        val message = match.groupValues[3]
        var head = kit.text("  ")
        if (prefix.isNotEmpty()) head = head.append(kit.text(prefix, ChatPalette.ORANGE))
        head = head.append(kit.text(className.substringBeforeLast('.', "").let { if (it.isEmpty()) "" else "$it." }, ChatPalette.FAINT))
            .append(kit.text(className.substringAfterLast('.'), ChatPalette.DANGER))
        if (message.isNotEmpty()) head = head.append(kit.text(": ", ChatPalette.SOFT)).append(kit.text(message, ChatPalette.TEXT))
        return head to false
    }
    return kit.text("    $text", ChatPalette.FAINT) to false
}

/**
 * The whole stack trace colored like source code: exception classes in red with their package dimmed, the plugin's
 * own frames in amber and green, frames of the JDK and the server dimmed. In chat the dimmed frames of one run are
 * folded into a single line and the list is cut; the console shows every line.
 */
private fun traceView(kit: ChatKit, trace: List<String>, full: Boolean): Pair<List<Component>, Boolean> {
    val rendered = trace.map { traceLine(kit, it) }
    if (full) return rendered.map { it.first } to false
    val out = mutableListOf<Component>()
    var folded = 0
    var cut = 0
    fun flush() {
        if (folded == 0) return
        out += kit.text("    · … $folded ${plural(folded.toLong(), "служебный вызов", "служебных вызова", "служебных вызовов")}", ChatPalette.FAINT)
        folded = 0
    }
    rendered.forEach { (component, framework) ->
        if (framework) {
            folded++
            return@forEach
        }
        flush()
        if (out.size >= CARD_TRACE_LINES) cut++ else out += component
    }
    flush()
    if (cut > 0) out += kit.note("    … ещё $cut строк, их даёт кнопка [скопировать]", ChatPalette.FAINT)
    return out to (cut > 0 || rendered.any { it.second })
}

/** The detail card of one event, or the timeline of its occurrences when [repeats] is set. */
private fun card(
    sender: CommandSender,
    kit: ChatKit,
    journal: JournalStore,
    id: String?,
    repeats: Boolean,
    fullTrace: Boolean = false,
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
        kit.button("назад", "/pnevents", "К списку событий", ChatPalette.ACCENT),
        if (repeats) kit.button("карточка", "/pnevents view $id", "Вернуться к карточке", ChatPalette.ACCENT)
        else if (group.size > 1) kit.button("повторы", "/pnevents times $id", "Когда это повторялось", ChatPalette.ACCENT)
        else null,
        if (traceHidden) kit.button("показать всё", "/pnevents full $id", "Стек вызовов целиком, ничего не свёрнуто", ChatPalette.ACCENT)
        else if (fullTrace) kit.button("свернуть", "/pnevents view $id", "Снова свернуть служебные вызовы", ChatPalette.ACCENT)
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

/** The display zone as `Europe/Moscow (UTC+03:00)`, so nobody has to guess which clock a time uses. */
private fun zoneLabel(): String {
    val zone = DisplayZone.zone
    val offset = zone.rules.getOffset(java.time.Instant.now()).id.let { if (it == "Z") "+00:00" else it }
    return "${zone.id} (UTC$offset)"
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
