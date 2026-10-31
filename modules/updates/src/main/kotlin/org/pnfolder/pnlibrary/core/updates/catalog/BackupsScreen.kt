package org.pnfolder.pnlibrary.core.updates.catalog

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackup
import org.pnfolder.pnlibrary.common.text.RussianText
import java.time.Instant
import java.time.ZoneId

/** Retained rollback sets with their age; paths and file contents are never shown. */
internal class BackupsScreen(routes: UpdateCatalogRoutes, now: Instant, zone: ZoneId) : CatalogStyle(routes, now, zone) {
    fun backups(all: List<UpdateBackup>, page: Int, query: BackupQuery = BackupQuery()): List<Component> {
        require(page > 0) { "Page must be positive" }
        val backups = query.apply(all)
        val selected = backups.onPage(page)
        val counted = RussianText.counted(backups.size.toLong(), "набор", "набора", "наборов")
        val result = mutableListOf(
            header("Резервы", text(if (query.isDefault) counted else "$counted из ${all.size}", ChatPalette.SOFT)),
            note("Сохранённые версии плагинов для отката."),
            note("Откат вернёт версии из строки и применится после перезапуска. Снова обновиться: ${routes.check}."),
            blank(),
        )
        filterLines(query)?.let { result += it }
        selected.forEach { result += rows(it) }
        if (selected.isEmpty()) {
            result += note(
                when {
                    all.isEmpty() -> "Резервов пока нет: они появятся после обновления."
                    backups.isEmpty() -> "По этим условиям наборов нет."
                    else -> "На этой странице пусто."
                },
            )
        }
        result += blank()
        actions(
            button("[Плагины]", routes.list, "Все зарегистрированные плагины", ChatPalette.ACCENT),
            button("[История]", routes.history, "Кто, когда и что обновлял и откатывал", ChatPalette.ACCENT),
        )?.let { result += it }
        pager(page, pageCount(backups.size), query.command(routes.backups), "Предыдущая", "Следующая")?.let { result += it }
        return framed(result)
    }

    /** The active filters and the buttons that change the order or clear them; for a reader that cannot click, the commands. */
    private fun filterLines(query: BackupQuery): List<Component>? {
        val active = listOfNotNull(
            query.plugin?.let { "плагин $it" },
            query.sinceText?.let { "с $it" },
            "порядок: ${query.order.title}",
        )
        val lines = mutableListOf<Component>(text("  Условия: ", ChatPalette.LABEL).append(text(active.joinToString(" · "), ChatPalette.TEXT)))
        val orders = BackupOrder.values().map { order ->
            if (order == query.order) selected(order.title, ChatPalette.ACCENT)
            else button("[${order.title}]", query.copy(order = order).command(routes.backups), "Порядок: ${order.title}", ChatPalette.ACCENT)
        }
        val clear = if (query.isDefault) null else button("[Сбросить]", routes.backups, "Все наборы, сначала новые", ChatPalette.WARN)
        actions(*(orders + clear).toTypedArray())?.let { lines += it }
        if (!routes.interactive) {
            val filters = "[plugin <плагин>] [since ${BackupQuery.SINCE_HELP}] [sort new|old|plugin] [страница]"
            lines += text("  Фильтры: ${routes.backups} $filters", ChatPalette.LABEL)
        }
        return lines
    }

    /** A set's versions and age with buttons that open the rollback confirmations, never the rollback itself. */
    private fun rows(backup: UpdateBackup): List<Component> {
        val restores = backup.components.joinToString { "${it.product} ${it.backupVersion?.let(::version) ?: "?"}" }
        val card = listOf(
            text("Резервный набор", ChatPalette.TEXT),
            hoverField("ID", text(backup.id.toString(), ChatPalette.LABEL)),
            hoverField("Создан", text(backup.createdAt?.let { "${fullTime(it)} · ${age(it)}" } ?: "дата не записана", ChatPalette.TEXT)),
        ) + backup.components.map { component ->
            hoverField("Вернёт", text("${component.product} ${component.backupVersion?.let(::version) ?: "?"}", ChatPalette.TEXT)
                .append(text(" (сейчас ${component.updatedVersion?.let(::version) ?: "?"})", ChatPalette.SOFT)))
        } + hoverField("Конфигураций", text("${backup.configurationFiles}", ChatPalette.TEXT))
        var row = text("  ").append(text(restores, ChatPalette.TEXT))
            .append(text("  ")).append(text(backup.createdAt?.let(::age) ?: "дата не записана", ChatPalette.SOFT))
            .hoverEvent(tooltip(*card.toTypedArray()))
            .append(spaced(button("[Откат]", routes.rollback(backup.id), "Открыть подтверждение отката.\nКонфигурации не меняются.", ChatPalette.WARN)))
        if (backup.configurationFiles > 0) {
            row = row.append(spaced(button("[+конфиги]", routes.rollback(backup.id, true),
                "Открыть подтверждение отката JAR\nи выбранных конфигураций.", ChatPalette.ORANGE)))
        }
        val rows = mutableListOf(row)
        if (!routes.interactive) {
            rows += text("      откат: ${routes.rollback(backup.id)}", ChatPalette.LABEL)
            if (backup.configurationFiles > 0) rows += text("      с конфигами: ${routes.rollback(backup.id, true)}", ChatPalette.LABEL)
        }
        return rows
    }
}
