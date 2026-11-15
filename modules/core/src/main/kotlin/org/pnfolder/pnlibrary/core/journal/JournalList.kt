package org.pnfolder.pnlibrary.core.journal

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.pnfolder.pnlibrary.api.chat.ChatKit
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.commands.CommandSender
import org.pnfolder.pnlibrary.common.text.RussianText
import java.util.Locale

/** Filter words in the order of the tabs: the command word, the tab label and the levels it shows. */
private val FILTERS = listOf(
    Triple("all", "все", emptySet<JournalLevel>()),
    Triple("error", "ошибки", setOf(JournalLevel.ERROR)),
    Triple("warning", "предупр.", setOf(JournalLevel.WARNING)),
    Triple("info", "инфо", setOf(JournalLevel.INFO)),
)

/** Prints one page of the journal for the filter and page named in [arguments], or tells which level words exist. */
fun list(sender: CommandSender, kit: ChatKit, journal: JournalStore, arguments: List<String>, root: String) {
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

    val lines = listHead(kit, groups.size, tabs(kit, everything, filter.first, root)).toMutableList()
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

private fun listHead(kit: ChatKit, count: Int, tabs: Component): List<Component> = listOf(
    kit.blank(),
    kit.blank(),
    kit.header("События", kit.text("$count ${RussianText.plural(count.toLong(), "запись", "записи", "записей")}", ChatPalette.LABEL)),
    tabs,
    kit.blank(),
)

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
                .hoverEvent(kit.tooltip(kit.text("Нажмите, чтобы показать: $label", ChatPalette.ACCENT), kit.blank(), kit.text("$root $word",
                    ChatPalette.LABEL))),
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
