package org.pnfolder.pnlibrary.core.menu

import org.pnfolder.pnlibrary.api.chat.ChatTone
import org.pnfolder.pnlibrary.api.updates.UpdateState
import org.pnfolder.pnlibrary.api.updates.backup.BackupKind
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackup
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackupItem
import org.pnfolder.pnlibrary.api.updates.backup.UpdateHistoryEntry
import org.pnfolder.pnlibrary.core.updates.catalog.CatalogLabels
import org.pnfolder.pnlibrary.core.updates.history.BackupItemText

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
                    entries.map { entry ->
                        val item = historyItem(entry, NOTE_PREVIEW_LINES, Style.left("подробности"))
                        Row(item, go { historyEntry(it, entry) { who -> history(who, product, page) } })
                    }
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
                    all.sortedByDescending { it.createdAt }.map { backup ->
                        Row(backupItem(backup, Style.left("выбрать откат")), go { backupChoice(it, backup) })
                    }
                }
                list(player, "Резервные копии", rows, page, PAGE_SIZE, { who, to -> backups(who, to) }, { routes.home(it) }, "Резервных копий нет")
            }
        }
    }

    private fun backupChoice(player: MenuViewer, backup: UpdateBackup) {
        val builder = frame(Menus.chest(title("Откат")).rows(SMALL_ROWS), SMALL_ROWS)
        builder.slot(4, backupItem(backup))
        val plain = Style.lore(
            listOf("Вернутся сохранённые JAR.", "Конфигурации не изменятся.", "Применится после перезапуска.") +
                if (databasesOf(backup).isEmpty()) emptyList() else listOf("Базы данных не изменятся: их возвращают кнопками справа."),
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
        databasesOf(backup).take(DATABASE_BUTTONS).forEachIndexed { index, item ->
            val lore = Style.lore(
                listOf("Заменит данные базы сохранёнными.", "Спросит подтверждение."),
                listOf("Размер копии" to BackupItemText.size(item.bytes)), Style.left("вернуть базу"),
            )
            builder.button(DATABASE_FIRST + index, icon(Icons.BACKUPS, "CHEST", "Вернуть базу «${item.name}»", lore, danger = true), go { who ->
                allowed(who, ROLLBACK) { databaseConfirm(who, backup, item) }
            })
        }
        builder.button(SMALL_BACK, backItem("Отмена"), go { backups(it, 0) })
        show(player, builder.build())
    }

    private fun databasesOf(backup: UpdateBackup) = backup.items.filter { it.kind == BackupKind.DATABASE }

    /** The last question before a database is replaced: what is lost, what is kept, and a way out. */
    private fun databaseConfirm(player: MenuViewer, backup: UpdateBackup, item: UpdateBackupItem) {
        val builder = frame(Menus.chest(title("Вернуть базу")).rows(SMALL_ROWS), SMALL_ROWS)
        val warning = Style.lore(
            listOf(
                "Таблицы базы «${item.name}» будут заменены теми, что были до обновления.",
                "Всё, что записано в базу с тех пор, пропадёт: игроки, балансы, прогресс.",
                "Текущее состояние сохранится отдельной копией на сервере, вернуть его можно командой.",
            ),
            listOf("Плагин" to item.product.value, "Копия" to BackupItemText.size(item.bytes)), Style.left("подтвердить"),
        )
        builder.slot(4, icon(Icons.BACKUPS, "CHEST", "Вы можете потерять данные", warning, danger = true))
        builder.button(11, icon(Icons.ROLLBACK, "TNT", "Да, заменить базу", warning, danger = true), go { who ->
            allowed(who, ROLLBACK) { restoreDatabase(who, backup, item) }
        })
        builder.button(SMALL_BACK, backItem("Отмена"), go { backupChoice(it, backup) })
        show(player, builder.build())
    }

    private fun restoreDatabase(player: MenuViewer, backup: UpdateBackup, item: UpdateBackupItem) {
        player.close()
        data.restoreDatabase(backup.id, item.product.value, item.name, player.actor).whenComplete { _, error ->
            sync {
                if (!player.online) return@sync
                if (error != null) tell(player, ChatTone.FAILURE, "Не удалось вернуть базу.", reason(error))
                else tell(player, ChatTone.SUCCESS, "База «${item.name}» возвращена.", "Перезапустите плагин ${item.product.value}, чтобы он перечитал данные.")
            }
        }
    }

    private fun historyEntry(player: MenuViewer, entry: UpdateHistoryEntry, back: (MenuViewer) -> Unit) {
        val builder = frame(Menus.chest(title("Запись истории")).rows(DETAIL_ROWS), DETAIL_ROWS)
        builder.slot(4, historyItem(entry, NOTE_LINES))
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

    private fun historyItem(entry: UpdateHistoryEntry, noteLines: Int, vararg clicks: String): ItemSpec {
        val failed = entry.state == UpdateState.FAILED
        val parameters = buildList {
            add("Когда" to time(entry.occurredAt))
            add("Кто" to entry.actor.name)
            entry.changes.forEach { add(it.product.value to "${it.from} » ${it.to}") }
        }
        val note = entry.message?.let { wrap(it, limit = noteLines) }.orEmpty()
            .map { if (failed) Style.danger(it) else Style.description(it) }
        val title = "${CatalogLabels.operationLabel(entry.operation)}: ${CatalogLabels.stateLabel(entry.state)}"
        return icon(null, if (failed) "BARRIER" else "PAPER", title, Style.lore(emptyList(), parameters, *clicks, extra = note), danger = failed)
    }

    private fun backupItem(backup: UpdateBackup, vararg clicks: String): ItemSpec {
        val parameters = buildList {
            backup.createdAt?.let { add("Создана" to time(it)) }
            backup.components.forEach { add(it.product.value to "${it.backupVersion ?: "?"} (было ${it.updatedVersion ?: "?"})") }
            backup.items.forEach { add("Сохранено" to BackupItemText.describe(it)) }
        }
        val lore = Style.lore(emptyList(), parameters, *clicks)
        return icon(Icons.BACKUPS, "CHEST", "Резервный набор", lore)
    }
}
