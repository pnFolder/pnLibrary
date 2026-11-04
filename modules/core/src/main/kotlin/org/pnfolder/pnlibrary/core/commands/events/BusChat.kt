package org.pnfolder.pnlibrary.core.commands.events

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.pnfolder.pnlibrary.api.chat.ChatKit
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.chat.ChatReader
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.events.EventInspector
import org.pnfolder.pnlibrary.api.events.ListenerInfo

internal fun kit(context: CommandContext) = ChatKit(ChatReader.of(context.sender))

/** A bar as long as [value] is against [busiest], at least one block. */
internal fun bar(kit: ChatKit, value: Long, busiest: Long): String {
    val width = ((value * BAR_WIDTH + busiest - 1) / busiest.coerceAtLeast(1)).toInt().coerceIn(1, BAR_WIDTH)
    return (if (kit.reader.ascii) "#" else "█").repeat(width)
}

internal fun dayTitle(date: java.time.LocalDate, today: java.time.LocalDate): String = when (date) {
    today -> "Сегодня"
    today.minusDays(1) -> "Вчера"
    else -> DAY.format(date)
}

internal fun hints(kit: ChatKit): List<Component> =
    if (kit.reader.interactive) emptyList()
    else kit.commandHints(
        "$ROOT [all|listened|called|denied] [страница]" to "список событий",
        "$ROOT view <событие>" to "карточка события",
        "$ROOT listeners" to "все слушатели",
        "$ROOT slow [страница]" to "самые медленные слушатели",
        "$ROOT export [csv|json]" to "выгрузить счётчики в файл",
        "$ROOT range [1h|24h|7d|30d]" to "вызовы за период",
        "$ROOT top [calls|denied]" to "самые частые события",
        "$ROOT errors [страница]" to "слушатели с ошибками",
        "$ROOT plugin <плагин>" to "слушатели одного плагина",
        "$ROOT trace <событие>" to "записывать вызовы",
        "$ROOT traces" to "записанные вызовы",
        "$ROOT history [страница]" to "вызовы по дням",
        "$ROOT history day <дата> [страница]" to "события одного дня",
        "$ROOT history of <событие>" to "вызовы одного события по дням",
    )

internal fun footer(kit: ChatKit, @Suppress("UNUSED_PARAMETER") root: String, vararg buttons: Component?): List<Component> =
    listOfNotNull(kit.actions(*buttons))

internal fun pager(kit: ChatKit, base: String, page: Int, pages: Int): Component {
    fun link(target: Int, label: String) = kit.option(label, "$base $target", "Страница $target из $pages", ChatPalette.SOFT)
    var line = kit.text("  ")
    line = line.append(if (page > 1) link(page - 1, "‹ назад") else kit.text("[‹ назад]", ChatPalette.FAINT))
    line = line.append(kit.text("  $page / $pages  ", ChatPalette.ACCENT))
    return line.append(if (page < pages) link(page + 1, "дальше ›") else kit.text("[дальше ›]", ChatPalette.FAINT))
}

internal fun pageCount(total: Int): Int = ((total + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)

internal fun shortName(name: String): String = name.substringAfterLast('.')

internal fun micros(nanos: Long): String =
    if (nanos >= NANOS_PER_MILLI) "%.1f мс".format(nanos / NANOS_PER_MILLI.toDouble())
    else "%.1f мкс".format(nanos / NANOS_PER_MICRO.toDouble())

/** A listener as a row: owner, priority and flags, with the source and counters in the hover card. */
internal fun listenerRow(kit: ChatKit, info: ListenerInfo, withEvent: Boolean): Component {
    val owner = info.module?.let { "${info.plugin}/$it" } ?: info.plugin.toString()
    var line = kit.text("   ").append(kit.text(owner, ChatPalette.ACCENT))
    if (withEvent) line = line.append(kit.dot()).append(kit.text(shortName(info.eventType), ChatPalette.TEXT))
    line = line.append(kit.text("  приоритет ${info.priority}", ChatPalette.SOFT))
    if (info.ignoreDenied) line = line.append(kit.text("  ignoreDenied", ChatPalette.INFO))
    if (info.failures > 0) line = line.append(kit.text("  ошибок ${info.failures}", ChatPalette.DANGER))
    if (!kit.reader.interactive) return line.append(kit.text("  ${info.source}", ChatPalette.FAINT))
    return line.hoverEvent(
        kit.tooltip(
            kit.text(owner, ChatPalette.ACCENT),
            kit.blank(),
            kit.hoverField("Событие", kit.text(info.eventType, ChatPalette.TEXT)),
            kit.hoverField("Источник", kit.text(info.source, ChatPalette.TEXT)),
            kit.hoverField("Приоритет", kit.text(info.priority.toString(), ChatPalette.TEXT)),
            kit.hoverField("Запусков", kit.text(info.invocations.toString(), ChatPalette.TEXT)),
            kit.hoverField("Среднее время", kit.text(micros(info.averageNanos), ChatPalette.TEXT)),
            kit.hoverField("Ошибок", kit.text(info.failures.toString(), if (info.failures > 0) ChatPalette.DANGER else ChatPalette.TEXT)),
        ),
    ).clickEvent(ClickEvent.runCommand("$ROOT view ${info.eventType}"))
}

/** The full class name for a typed full or short name; the same text back when nothing matches. */
internal fun EventInspector.resolve(typed: String): String {
    val known = events().map { it.eventType }
    if (typed in known) return typed
    return known.singleOrNull { shortName(it).equals(typed, ignoreCase = true) } ?: typed
}
