package ru.privatenull.pnlibrary.core.updates.catalog

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.TextColor
import ru.privatenull.pnlibrary.api.updates.UpdateHistoryEntry
import ru.privatenull.pnlibrary.api.updates.UpdateState
import ru.privatenull.pnlibrary.core.ui.ChatPalette
import ru.privatenull.pnlibrary.core.updates.install.ComponentLedger
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Durable update events newest first, grouped by day, with who did what in the hover cards. */
internal class HistoryScreen(routes: UpdateCatalogRoutes, now: Instant, zone: ZoneId) : CatalogStyle(routes, now, zone) {
    /** What an event led to: a short label for the row and the full wording for its hover card. */
    private class Outcome(val label: String, val color: TextColor, val detail: String = label)

    fun history(entries: List<UpdateHistoryEntry>, page: Int, totalPages: Int?, product: String?): List<Component> {
        require(page > 0) { "page must be positive" }
        val result = mutableListOf(header("История", product?.let { text(it, ChatPalette.SOFT) }))
        var day: LocalDate? = null
        entries.forEach { entry ->
            val entryDay = entry.occurredAt.atZone(zone).toLocalDate()
            if (entryDay != day) {
                day = entryDay
                result += blank()
                result += text("  ${dayLabel(entryDay)}", ChatPalette.ACCENT)
            }
            result += rows(entry, offerFilter = product == null)
        }
        if (entries.isEmpty()) result += listOf(blank(), note(if (page == 1) "Событий пока нет." else "На этой странице пусто."))
        result += blank()
        val base = if (product == null) routes.history else routes.history(product)
        val pages = totalPages ?: if (entries.size >= UpdateCatalogView.PAGE_SIZE) page + 1 else page
        actions(
            product?.let { button("[Все события]", routes.history, "История всех плагинов", ChatPalette.ACCENT) },
            product?.let { button("[Карточка]", routes.info(it), "Сведения о плагине $it", ChatPalette.ACCENT) },
            button("[Плагины]", routes.list, "Все зарегистрированные плагины", ChatPalette.ACCENT),
        )?.let { result += it }
        pager(page, pages, base, "Новее", "Старее")?.let { result += it }
        if (!routes.interactive) {
            result += commandHints(
                "$base <страница>" to "страница",
                "${routes.root} history plugin <плагин>" to "события одного плагина",
                routes.list to "плагины",
            )
        }
        return framed(result)
    }

    /** One row per changed component; clicking a row filters the history to that component. */
    private fun rows(entry: UpdateHistoryEntry, offerFilter: Boolean): List<Component> {
        val outcome = outcome(entry)
        val lead = text("    ")
        val tail = text("  ").append(text(outcome.label, outcome.color))
        if (entry.changes.isEmpty()) {
            val body = entry.message?.takeIf { it.isNotBlank() }?.take(MAX_LINE) ?: CatalogLabels.operationLabel(entry.operation)
            return listOf(lead.append(text(body, ChatPalette.TEXT)).append(tail).hoverEvent(card(entry, outcome, null, false)))
        }
        return entry.changes.map { change ->
            val product = change.product.toString()
            val transition = when (entry.operation) {
                "ROLLBACK", "HEALTH_ROLLBACK" -> text("откат к ${change.from?.let { version(it.toString()) } ?: "не установлено"}", ChatPalette.SOFT)
                ComponentLedger.OPERATION_ADDED -> text(version(change.to.toString()), ChatPalette.SOFT)
                ComponentLedger.OPERATION_REMOVED, ComponentLedger.OPERATION_SETTINGS_CHANGED ->
                    text(version((change.from ?: change.to).toString()), ChatPalette.SOFT)
                else -> versions(change.from?.toString() ?: "-", change.to.toString())
            }
            var row = lead.append(text("$product  ", ChatPalette.TEXT)).append(transition).append(tail)
                .hoverEvent(card(entry, outcome, product, offerFilter))
            if (offerFilter && routes.interactive) row = row.clickEvent(ClickEvent.runCommand(routes.history(product)))
            row
        }
    }

    private fun card(entry: UpdateHistoryEntry, outcome: Outcome, product: String?, offerFilter: Boolean): HoverEvent<Component> =
        tooltip(*listOfNotNull(
            product?.let { text(it, ChatPalette.TEXT) },
            hoverField("Событие", text(CatalogLabels.operationLabel(entry.operation), ChatPalette.TEXT)),
            hoverField("Итог", text(outcome.detail, outcome.color)),
            hoverField("Кто", text(entry.actor.name, ChatPalette.TEXT)),
            hoverField("Когда", text("${fullTime(entry.occurredAt)} · ${entry.occurredAt.atOffset(ZoneOffset.UTC)}", ChatPalette.TEXT)),
            hoverField("План", text(entry.planId.toString(), ChatPalette.LABEL)),
            entry.message?.takeIf { it.isNotBlank() }?.let { text(it.take(MAX_MESSAGE), ChatPalette.SOFT) },
            product?.takeIf { offerFilter }?.let { blank() },
            product?.takeIf { offerFilter }?.let { text("Показать только $it", ChatPalette.ACCENT) },
            product?.takeIf { offerFilter }?.let { text(routes.history(it), ChatPalette.LABEL) },
        ).toTypedArray())

    private fun outcome(entry: UpdateHistoryEntry): Outcome = when {
        entry.operation == ComponentLedger.OPERATION_ADDED -> Outcome("установлен", ChatPalette.OK)
        entry.operation == ComponentLedger.OPERATION_REMOVED -> Outcome("удалён", ChatPalette.DANGER)
        entry.operation == ComponentLedger.OPERATION_VERSION_CHANGED -> Outcome("изменена вручную", ChatPalette.ORANGE, "версия изменена вручную")
        entry.operation == ComponentLedger.OPERATION_SETTINGS_CHANGED ->
            Outcome(entry.message?.takeIf { it.isNotBlank() }?.take(MAX_LINE) ?: "настройки изменены", ChatPalette.WARN)
        entry.state == UpdateState.FAILED -> Outcome("ошибка", ChatPalette.DANGER)
        entry.operation == "HEALTH" && entry.state == UpdateState.UP_TO_DATE -> Outcome("применено", ChatPalette.OK, "применено после перезапуска")
        entry.operation == "HEALTH_ROLLBACK" && entry.state == UpdateState.ROLLED_BACK ->
            Outcome("подтверждён", ChatPalette.OK, "откат подтверждён после перезапуска")
        entry.operation == "ROLLBACK" || entry.state == UpdateState.ROLLED_BACK ->
            Outcome("ждёт перезапуска", ChatPalette.ORANGE, "откат подготовлен, применится после перезапуска")
        entry.state == UpdateState.UPDATE_STAGED -> Outcome("подготовлено", ChatPalette.ORANGE, "подготовлено, применится после перезапуска")
        entry.state == UpdateState.UPDATE_AVAILABLE -> Outcome("доступна новая", ChatPalette.INFO, "доступна новая версия")
        else -> Outcome(CatalogLabels.shortStateLabel(entry.state), CatalogLabels.stateColor(entry.state), CatalogLabels.stateLabel(entry.state))
    }
}
