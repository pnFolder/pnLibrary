package org.pnfolder.pnlibrary.core.menu

import org.pnfolder.pnlibrary.api.chat.ChatTone
import org.pnfolder.pnlibrary.common.time.DisplayZone
import org.pnfolder.pnlibrary.core.lifecycle.JournalGroup
import java.time.Instant

internal class JournalScreens(session: MenuSession, routes: MenuRoutes) : MenuScreens(session, routes) {
    /** The journal of the last day: equal events merged, each opening its card in the chat. */
    fun journal(player: MenuViewer, level: String?, page: Int) {
        val title = "Журнал сервера" + (level?.let { " " + LEVELS.getValue(it) } ?: "")
        val next = LEVEL_CYCLE[(LEVEL_CYCLE.indexOf(level) + 1) % LEVEL_CYCLE.size]
        val cycle = icon(
            Icons.SETTINGS, "HOPPER", "Уровень: ${level?.let { LEVELS.getValue(it) } ?: "все"}",
            Style.lore(
                listOf("Какие события показывать."), emptyList(), Style.left("переключить"),
                extra = Style.choices(LEVEL_CYCLE.map { it?.let { name -> LEVELS.getValue(name) } ?: "все" }, LEVEL_CYCLE.indexOf(level)),
            ),
        )
        list(
            player, title, journalRows(level) { who -> journal(who, level, page) }, page, PAGE_SIZE,
            { who, to -> journal(who, level, to) }, { routes.home(it) }, "За сутки событий нет",
            extra = { it.button(FILTER, cycle, go { who -> journal(who, next, 0) }) },
        )
    }

    fun journalRows(level: String?, back: (MenuViewer) -> Unit): List<Row> =
        data.journalGroups(level, JOURNAL_HOURS, JOURNAL_LIMIT).map { group ->
            Row(
                journalItem(group),
                goClick { who, click ->
                    when (click) {
                        ClickType.RIGHT -> chat(who, "pn events times ${group.id}")
                        ClickType.SHIFT_LEFT -> chat(who, "pn events full ${group.id}")
                        else -> journalEvent(who, group.id, back)
                    }
                },
            )
        }

    private fun chat(player: MenuViewer, command: String) {
        player.close()
        runCommand(player, command)
    }

    private fun journalItem(group: JournalGroup): ItemSpec {
        val material = journalMaterial(group.level)
        val parameters = listOf(
            "Источник" to group.source,
            "Когда" to time(Instant.ofEpochMilli(group.lastMillis)),
            "Повторов" to "${group.count}",
        )
        val lore = Style.lore(
            listOf(group.message.take(MESSAGE_PREVIEW)), parameters,
            Style.left("открыть событие"), Style.right("все повторы в чат"), Style.shiftLeft("полный текст в чат"),
        )
        return icon(null, material, "${LEVELS.getValue(group.level)}: ${group.source}", lore, danger = group.level == "error", warn = group.level == "warning")
    }

    /** One journal event with its text, stack trace, repeats and facts; [back] returns to the list it was opened from. */
    private fun journalEvent(player: MenuViewer, id: String, back: (MenuViewer) -> Unit) {
        val detail = data.journalDetail(id)
        if (detail == null) {
            tell(player, ChatTone.INFO, "Это событие уже вышло из журнала.")
            return back(player)
        }
        val builder = frame(Menus.chest(title("Событие")).rows(DETAIL_ROWS), DETAIL_ROWS)
        val error = detail.level == "error"
        val summary = Style.lore(
            wrap(detail.message),
            listOf(
                "Важность" to LEVELS.getValue(detail.level),
                "Источник" to detail.source,
                "Повторов" to "${detail.times.size}",
                "Последний раз" to stamp(Instant.ofEpochMilli(detail.times.first())),
                "" to ago(Instant.ofEpochMilli(detail.times.first())),
            ),
        )
        builder.slot(4, icon(null, journalMaterial(detail.level), "Событие ${detail.source}", summary, danger = error, warn = detail.level == "warning"))
        detail.stackTrace?.let { trace ->
            val lore = Style.lore(emptyList(), emptyList(), Style.left("весь текст в чат"), extra = wrap(trace, limit = TRACE_LINES).map { Style.danger(it) })
            builder.button(
                20,
                icon(Icons.PROBLEMS, "BOOK", "Текст ошибки", lore, danger = true),
                go { who -> who.close(); runCommand(who, "pn events full $id") },
            )
        }
        val moments = detail.times.map { Instant.ofEpochMilli(it) }
        val more = moments.size - TIMES_PREVIEW
        val timesLore = Style.lore(
            moments.take(TIMES_PREVIEW).map { stamp(it) },
            listOf("Пояс" to DisplayZone.zone.id),
            *if (more > 0) arrayOf(Style.left("все ${moments.size} раз")) else emptyArray(),
            extra = if (more > 0) listOf(Style.description("и ещё $more")) else emptyList(),
        )
        builder.button(
            22, icon(Icons.CLOCK, "CLOCK", "Когда случалось", timesLore, alternative = "WATCH"),
            go { who -> if (more > 0) journalTimes(who, id, 0, back) },
        )
        if (detail.details.isNotEmpty()) {
            val facts = detail.details.entries.take(DETAILS_SHOWN).map { (key, value) -> key to value.take(MESSAGE_PREVIEW) }
            builder.slot(24, icon(Icons.CHANGELOG, "PAPER", "Подробности", Style.lore(emptyList(), facts)))
        }
        builder.button(
            30,
            icon(
                Icons.CHECK, "COMPASS", "Показать в чате",
                Style.lore(listOf("То же самое, что команда /pn events view."), emptyList(), Style.left("открыть в чате")),
            ),
            go { who -> who.close(); runCommand(who, "pn events view $id") },
        )
        builder.button(DETAIL_BACK, backItem(), go { back(it) })
        show(player, builder.build())
    }

    /** Every time a journal event happened, newest first, in the configured time zone. */
    private fun journalTimes(player: MenuViewer, id: String, page: Int, back: (MenuViewer) -> Unit) {
        val detail = data.journalDetail(id)
        if (detail == null) {
            tell(player, ChatTone.INFO, "Это событие уже вышло из журнала.")
            return back(player)
        }
        val rows = detail.times.mapIndexed { index, millis ->
            val moment = Instant.ofEpochMilli(millis)
            val lore = Style.lore(emptyList(), listOf("Было" to ago(moment), "Номер" to "${detail.times.size - index} из ${detail.times.size}"))
            Row(icon(Icons.CLOCK, "CLOCK", stamp(moment), lore, alternative = "WATCH"))
        }
        list(player, "Когда случалось", rows, page, PAGE_SIZE, { who, to -> journalTimes(who, id, to, back) }, { journalEvent(it, id, back) }, "Времени нет")
    }

    private fun journalMaterial(level: String): String = when (level) {
        "error" -> "BARRIER"
        "warning" -> "GOLD_INGOT"
        else -> "PAPER"
    }
}
