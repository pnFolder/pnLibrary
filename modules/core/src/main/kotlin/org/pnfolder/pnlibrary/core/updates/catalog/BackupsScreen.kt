package org.pnfolder.pnlibrary.core.updates.catalog

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.updates.UpdateBackup
import org.pnfolder.pnlibrary.common.text.RussianText
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import java.time.Instant
import java.time.ZoneId

/** Retained rollback sets with their age; paths and file contents are never shown. */
internal class BackupsScreen(routes: UpdateCatalogRoutes, now: Instant, zone: ZoneId) : CatalogStyle(routes, now, zone) {
    fun backups(backups: List<UpdateBackup>, page: Int): List<Component> {
        require(page > 0) { "Page must be positive" }
        val selected = backups.onPage(page)
        val result = mutableListOf(
            header("Резервы", text(RussianText.counted(backups.size.toLong(), "набор", "набора", "наборов"), ChatPalette.SOFT)),
            note("Сохранённые версии плагинов для отката."),
            blank(),
        )
        selected.forEach { result += rows(it) }
        if (selected.isEmpty()) {
            result += note(if (backups.isEmpty()) "Резервов пока нет: они появятся после обновления." else "На этой странице пусто.")
        }
        result += blank()
        actions(
            button("[Плагины]", routes.list, "Все зарегистрированные плагины", ChatPalette.ACCENT),
            button("[История]", routes.history, "Кто, когда и что обновлял", ChatPalette.ACCENT),
        )?.let { result += it }
        pager(page, pageCount(backups.size), routes.backups, "Предыдущая", "Следующая")?.let { result += it }
        return framed(result)
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
