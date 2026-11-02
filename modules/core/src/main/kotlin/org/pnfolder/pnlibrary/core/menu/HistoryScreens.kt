package org.pnfolder.pnlibrary.core.menu

import org.pnfolder.pnlibrary.api.chat.ChatTone
import org.pnfolder.pnlibrary.api.updates.UpdateState
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackup
import org.pnfolder.pnlibrary.api.updates.backup.UpdateHistoryEntry
import org.pnfolder.pnlibrary.core.updates.catalog.CatalogLabels

internal class HistoryScreens(session: MenuSession, routes: MenuRoutes) : MenuScreens(session, routes) {
    fun history(player: MenuViewer, product: String?, page: Int) {
        val normalized = product?.lowercase()
        data.historyCount(normalized).thenCompose { total ->
            data.historyPage(page + 1, normalized).thenApply { total to it }
        }.whenComplete { result, error ->
            sync {
                if (!player.online) return@sync
                val (total, entries) = result ?: (0 to emptyList())
                val back: (MenuViewer) -> Unit = if (product == null) { who -> routes.home(who) } else { who -> routes.card(who, product) }
                val rows = if (error != null) {
                    emptyList()
                } else {
                    entries.map { entry -> Row(historyItem(entry), go { historyEntry(it, entry) { who -> history(who, product, page) } }) }
                }
                val title = if (product == null) "История" else "История $product"
                list(player, title, rows, page, HISTORY_PAGE, { who, to -> history(who, product, to) }, back, "Записей пока нет", total)
            }
        }
    }

    fun backups(player: MenuViewer, page: Int) {
        data.backups().whenComplete { all, error ->
            sync {
                if (!player.online) return@sync
                val rows = if (error != null) {
                    emptyList()
                } else {
                    all.sortedByDescending { it.createdAt }.map { backup -> Row(backupItem(backup), go { backupChoice(it, backup) }) }
                }
                list(player, "Резервные копии", rows, page, PAGE_SIZE, { who, to -> backups(who, to) }, { routes.home(it) }, "Резервных копий нет")
            }
        }
    }

    private fun backupChoice(player: MenuViewer, backup: UpdateBackup) {
        val builder = frame(Menus.chest(title("Откат")).rows(SMALL_ROWS), SMALL_ROWS)
        builder.slot(4, backupItem(backup, withHint = false))
        val plain = Style.lore(
            listOf("Вернутся сохранённые JAR.", "Конфигурации не изменятся.", "Применится после перезапуска."),
            emptyList(), Style.left("откатить"),
        )
        builder.button(11, icon(Icons.ROLLBACK, "TNT", "Откатить плагины", plain, danger = true), go { who ->
            allowed(who, ROLLBACK) { rollback(who, backup, restoreConfigurations = false) }
        })
        if (backup.configurationFiles > 0) {
            val full = Style.lore(
                listOf("Вернутся JAR и файлы настроек: ${backup.configurationFiles}.", "Применится после перезапуска."),
                emptyList(), Style.left("откатить"),
            )
            builder.button(13, icon(Icons.ROLLBACK, "TNT", "Откатить плагины и настройки", full, danger = true), go { who ->
                allowed(who, ROLLBACK) { rollback(who, backup, restoreConfigurations = true) }
            })
        }
        builder.button(SMALL_BACK, backItem("Отмена"), go { backups(it, 0) })
        show(player, builder.build())
    }

    /** One entry of the update history with the whole message and a button for every plugin it touched. */
    private fun historyEntry(player: MenuViewer, entry: UpdateHistoryEntry, back: (MenuViewer) -> Unit) {
        val builder = frame(Menus.chest(title("Запись истории")).rows(DETAIL_ROWS), DETAIL_ROWS)
        builder.slot(4, historyItem(entry, full = true))
        entry.changes.take(CHANGE_BUTTONS).forEachIndexed { index, change ->
            val product = change.product.value
            val known = data.snapshot(product) != null
            val lore = Style.lore(
                listOf("Версия изменилась."),
                listOf("Было" to "${change.from}", "Стало" to "${change.to}"),
                *if (known) arrayOf(Style.left("открыть карточку")) else emptyArray(),
            )
            val stack = icon(Icons.PLUGIN, "PAPER", product, lore, muted = !known)
            if (known) builder.button(CHANGE_FIRST + index, stack, go { routes.card(it, product) }) else builder.slot(CHANGE_FIRST + index, stack)
        }
        builder.button(DETAIL_BACK, backItem(), go { back(it) })
        show(player, builder.build())
    }

    private fun rollback(player: MenuViewer, backup: UpdateBackup, restoreConfigurations: Boolean) {
        player.close()
        data.rollback(backup.id, restoreConfigurations, player.actor).whenComplete { _, error ->
            sync {
                if (!player.online) return@sync
                if (error != null) tell(player, ChatTone.FAILURE, "Не удалось подготовить откат.", reason(error))
                else tell(player, ChatTone.SUCCESS, "Резервный набор подготовлен.", "Полностью перезапустите сервер, чтобы применить его.")
            }
        }
    }

    private fun historyItem(entry: UpdateHistoryEntry, full: Boolean = false): ItemSpec {
        val failed = entry.state == UpdateState.FAILED
        val parameters = buildList {
            add("Когда" to time(entry.occurredAt))
            add("Кто" to entry.actor.name)
            entry.changes.forEach { add(it.product.value to "${it.from} » ${it.to}") }
        }
        val note = entry.message?.let { wrap(it, limit = if (full) NOTE_LINES else 2) }.orEmpty()
            .map { if (failed) Style.danger(it) else Style.description(it) }
        val title = "${CatalogLabels.operationLabel(entry.operation)}: ${CatalogLabels.stateLabel(entry.state)}"
        val hints = if (full) emptyArray() else arrayOf(Style.left("подробности"))
        return icon(null, if (failed) "BARRIER" else "PAPER", title, Style.lore(emptyList(), parameters, *hints, extra = note), danger = failed)
    }

    private fun backupItem(backup: UpdateBackup, withHint: Boolean = true): ItemSpec {
        val parameters = buildList {
            backup.createdAt?.let { add("Создана" to time(it)) }
            backup.components.forEach { add(it.product.value to "${it.backupVersion ?: "?"} (было ${it.updatedVersion ?: "?"})") }
            if (backup.configurationFiles > 0) add("Файлов настроек" to "${backup.configurationFiles}")
        }
        val lore = Style.lore(emptyList(), parameters, *if (withHint) arrayOf(Style.left("выбрать откат")) else emptyArray())
        return icon(Icons.BACKUPS, "CHEST", "Резервный набор", lore)
    }
}
