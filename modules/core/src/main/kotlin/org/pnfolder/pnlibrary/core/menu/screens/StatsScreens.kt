package org.pnfolder.pnlibrary.core.menu.screens

import org.pnfolder.pnlibrary.core.menu.ItemSpec
import org.pnfolder.pnlibrary.core.menu.MenuRoutes
import org.pnfolder.pnlibrary.core.menu.MenuScreens
import org.pnfolder.pnlibrary.core.menu.MenuSession
import org.pnfolder.pnlibrary.core.menu.MenuViewer
import org.pnfolder.pnlibrary.core.menu.Menus
import org.pnfolder.pnlibrary.core.menu.style.DETAIL_BACK
import org.pnfolder.pnlibrary.core.menu.style.DETAIL_ROWS
import org.pnfolder.pnlibrary.core.menu.style.Icons
import org.pnfolder.pnlibrary.core.menu.style.Style
import org.pnfolder.pnlibrary.core.menu.style.backItem
import org.pnfolder.pnlibrary.core.menu.style.icon
import org.pnfolder.pnlibrary.core.menu.style.menuFrame
import org.pnfolder.pnlibrary.core.menu.style.menuTitle
import org.pnfolder.pnlibrary.core.stats.StatsSnapshot
/** Menu screens with usage statistics. */
internal class StatsScreens(session: MenuSession, routes: MenuRoutes) : MenuScreens(session, routes) {
    fun stats(player: MenuViewer) {
        val now = data.stats()
        val builder = menuFrame(Menus.chest(menuTitle("Статистика")).rows(DETAIL_ROWS), DETAIL_ROWS)
        builder.slot(4, overview(now))
        builder.slot(20, tasks(now))
        builder.slot(22, events(now))
        builder.slot(24, errors(now))
        builder.button(DETAIL_BACK, backItem(), go { routes.home(it) })
        show(player, builder.build())
    }

    private fun overview(now: StatsSnapshot) = icon(
        Icons.OVERVIEW, "NETHER_STAR", "Сервер сейчас",
        Style.lore(
            listOf("Числа без имён игроков и текстов: только счётчики и плагины."),
            listOf("Задач активно" to "${now.tasksActive}", "Ошибок за сутки" to "${now.errors24h}"),
        ),
    )

    private fun tasks(now: StatsSnapshot) = icon(
        Icons.CLOCK, "CLOCK", "Задачи",
        Style.lore(
            listOf("Что плагины делают по расписанию."),
            listOf(
                "Активно" to "${now.tasksActive}", "С ошибкой" to "${now.tasksFailed}",
                "Запусков" to "${now.taskRuns}", "Пропущено" to "${now.taskSkipped}",
            ),
        ),
        danger = now.tasksFailed > 0,
    )

    private fun events(now: StatsSnapshot) = icon(
        Icons.UPDATES, "REDSTONE_TORCH", "События",
        Style.lore(listOf("Как плагины общаются друг с другом."), listOf("Вызовов" to "${now.eventsCalled}", "Отменено" to "${now.eventsDenied}")),
    )

    private fun errors(now: StatsSnapshot): ItemSpec {
        val worst = now.errorSources.map { (source, count) -> source to "$count" }
        return icon(
            Icons.PROBLEMS, "BARRIER", "Ошибки за сутки",
            Style.lore(
                listOf(if (now.errors24h == 0) "Ошибок нет." else "Больше всего ошибок у:"),
                listOf("Ошибок" to "${now.errors24h}", "Предупреждений" to "${now.warnings24h}") + worst,
            ),
            danger = now.errors24h > 0,
        )
    }
}
