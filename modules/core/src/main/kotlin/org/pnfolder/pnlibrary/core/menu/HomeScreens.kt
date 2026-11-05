package org.pnfolder.pnlibrary.core.menu

import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity
import org.pnfolder.pnlibrary.api.updates.UpdateState
import org.pnfolder.pnlibrary.core.commands.tasks.TaskPeriod
import org.pnfolder.pnlibrary.core.commands.tasks.TaskState
import org.pnfolder.pnlibrary.core.updates.catalog.CatalogLabels

internal class HomeScreens(session: MenuSession, routes: MenuRoutes) : MenuScreens(session, routes) {
    /** The home page: counters and the way to every other screen. */
    fun home(player: MenuViewer) {
        val snapshots = snapshots()
        val pending = snapshots.count { it.state in CatalogLabels.PENDING }
        val errors = data.journalGroups("error", JOURNAL_HOURS, JOURNAL_LIMIT).size
        val problems = snapshots.count { it.state in CatalogLabels.PROBLEMS } + errors
        val builder = frame(Menus.chest(title("Главная")).rows(HOME_ROWS), HOME_ROWS)
        summary(builder, pending, problems)
        sections(builder, pending, problems, errors)
        network(builder)
        builder.button(HOME_CLOSE, closeItem(), go { it.close() })
        planButtons(builder, data.plan())
        show(player, builder.build())
    }

    private fun summary(builder: MenuBuilder, pending: Int, problems: Int) {
        builder.slot(
            4,
            icon(
                Icons.OVERVIEW, "NETHER_STAR", "${PnLibraryIdentity.NAME} ${data.version()}",
                Style.lore(
                    listOf("Библиотека для плагинов pnFolder: обновления, настройки и поддержка."),
                    listOf(
                        "Версия" to data.version(),
                        "Итог" to when {
                            problems > 0 -> "✖ проблем: $problems"
                            pending > 0 -> "✦ к обновлению: $pending"
                            else -> "✔ всё актуально"
                        },
                        "Плагинов" to "${connectedCount()}",
                        "Обновлений" to "$pending",
                        "Проблем" to "$problems",
                    ),
                ),
            ),
        )
    }

    private fun sections(builder: MenuBuilder, pending: Int, problems: Int, errors: Int) {
        builder.button(11,
            section(Icons.PLUGINS, "BOOKSHELF", "Плагины", "Все плагины, подключённые к библиотеке.", "Всего" to "${connectedCount()}"),
            go { routes.plugins(it, Filter.ALL, 0) })
        builder.button(13,
            section(Icons.UPDATES, "GOLD_INGOT", "Обновления", "Плагины, для которых вышла новая версия.", "Доступно" to "$pending"),
            go { routes.plugins(it, Filter.UPDATES, 0) })
        builder.button(15,
            section(Icons.PROBLEMS, "BARRIER", "Проблемы", "Ошибки проверки и блокировки обновлений.", "Найдено" to "$problems"),
            go { routes.plugins(it, Filter.PROBLEMS, 0) })
        builder.button(29, section(Icons.HISTORY, "BOOK", "История", "Проверки, обновления, откаты и ошибки."), go { routes.history(it, null, 0) })
        builder.button(
            30,
            section(
                Icons.PROBLEMS, "BOOK", "Журнал сервера", "Ошибки и предупреждения сервера за последние сутки.",
                "Ошибок" to if (errors > 0) "✖ $errors" else "✔ нет",
            ),
            go { routes.journal(it, null, 0) },
        )
        toolSections(builder)
    }

    /** Tasks, events, statistics, placeholders, backups, checking, settings, tools and support. */
    private fun toolSections(builder: MenuBuilder) {
        builder.button(
            32,
            section(Icons.CLOCK, "CLOCK", "Задачи плагинов", "Что плагины делают по расписанию и для чего.", "Всего" to "${data.tasks().size}"),
            go { allowed(it, TASKS) { routes.tasks(it, TaskPeriod.TODAY, TaskState.ALL, 0) } },
        )
        builder.button(
            31,
            section(Icons.UPDATES, "REDSTONE_TORCH", "События плагинов", "Как плагины общаются друг с другом: слушатели, вызовы, ошибки."),
            go { allowed(it, BUS) { routes.busHome(it) } },
        )
        builder.button(
            25,
            section(Icons.OVERVIEW, "COMPARATOR", "Статистика", "Задачи, события, ошибки и предупреждения за сутки."),
            go { allowed(it, STATS) { routes.stats(it) } },
        )
        builder.button(
            23,
            section(Icons.TOOLS, "NAME_TAG", "Плейсхолдеры", "Что плагины публикуют для текста и PlaceholderAPI."),
            go { allowed(it, PLACEHOLDERS) { routes.placeholders(it, 0) } },
        )
        builder.button(33, section(Icons.BACKUPS, "CHEST", "Резервные копии", "Откат к сохранённым версиям плагинов."), go { routes.backups(it, 0) })
        builder.button(
            24,
            icon(Icons.CHECK, "COMPASS", "Проверить всё", Style.lore(listOf("Спросить каталоги релизов прямо сейчас."), emptyList(), Style.left("проверить"))),
            go { routes.checkAll(it) },
        )
        builder.button(20, section(Icons.SETTINGS, "REDSTONE", "Настройки", "Автозагрузка и пауза для всех плагинов сразу."), go { routes.settings(it) })
        builder.button(22, section(Icons.TOOLS, "ANVIL", "Инструменты", "Статус сервера, отчёт для поддержки, валюты."), go { routes.tools(it) })
        builder.button(
            SUPPORT_SLOT,
            icon(Icons.SUPPORT, "PAPER", "Поддержка", Style.lore(listOf("Если возникла проблема, мы поможем."), emptyList(), Style.left("получить ссылку"))),
            go { routes.support(it) },
        )
    }

    private fun network(builder: MenuBuilder) {
        val received = host.link.network
        if (!remote && (received != null || host.link.enabled)) {
            val about = "Прокси и другие серверы сети: их плагины и обновления."
            val item = if (received == null) {
                section(Icons.OVERVIEW, "ENDER_EYE", "Сеть", about, "Данные" to "ещё не пришли")
            } else {
                section(Icons.OVERVIEW, "ENDER_EYE", "Сеть", about, "Серверов" to "${received.report.servers.size + 1}", "Данные" to ago(received.receivedAt))
            }
            builder.button(NETWORK_SLOT, item, go { who -> routes.network(who, 0) })
        }
    }

    private fun planButtons(builder: MenuBuilder, plan: PlanInfo?) {
        if (plan != null && plan.state == UpdateState.UPDATE_STAGED) {
            val restart = icon(
                Icons.RESTART, "DIAMOND", "Перезапуск",
                Style.lore(listOf("Обновления подготовлены."), emptyList(), Style.left("подтвердить в чате")),
            )
            builder.button(RESTART_SLOT, restart, go { it.close(); runCommand(it, "pn update restart") })
        }
        if (plan?.hasChanges == true) {
            val rollback = icon(
                Icons.ROLLBACK, "TNT", "Откатить план",
                Style.lore(listOf("Откатит все связанные плагины плана."), emptyList(), Style.left("подтвердить в чате")),
                danger = true,
            )
            builder.button(ROLLBACK_SLOT, rollback, go { it.close(); runCommand(it, "pn update rollback") })
        }
    }
}
