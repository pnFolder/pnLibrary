package org.pnfolder.pnlibrary.core.menu

import org.pnfolder.pnlibrary.api.updates.UpdateSnapshot
import org.pnfolder.pnlibrary.core.commands.TaskPeriod
import org.pnfolder.pnlibrary.core.commands.TaskState
import org.pnfolder.pnlibrary.core.link.NetworkReport
import org.pnfolder.pnlibrary.core.link.NetworkServer
import java.time.Duration

/** Builds every part of one menu and answers the routes between them. */
internal class MenuRouter(private val session: MenuSession) : MenuRoutes {
    private val homeScreens = HomeScreens(session, this)
    private val updateScreens = UpdateScreens(session, this)
    private val historyScreens = HistoryScreens(session, this)
    private val settingsScreens = SettingsScreens(session, this)
    private val journalScreens = JournalScreens(session, this)
    private val taskScreens = TaskScreens(session, this)
    private val networkScreens = NetworkScreens(session, this)
    private val groupScreens = GroupScreens(session, this)
    private val statsScreens = StatsScreens(session, this)
    private val busScreens = BusScreens(session, this)
    private val busHistoryScreens = BusHistoryScreens(session, this)

    override fun aboutOf(name: String): EventAbout? = taskScreens.aboutOf(name)
    override fun backups(player: MenuViewer, page: Int) = historyScreens.backups(player, page)
    override fun busDays(player: MenuViewer, event: String?, page: Int) = busHistoryScreens.busDays(player, event, page)
    override fun busEvent(player: MenuViewer, name: String, back: (MenuViewer) -> Unit) = busScreens.busEvent(player, name, back)
    override fun busHome(player: MenuViewer) = busScreens.busHome(player)
    override fun busTraces(player: MenuViewer, event: String?, page: Int) = busHistoryScreens.busTraces(player, event, page)
    override fun card(player: MenuViewer, product: String) = updateScreens.card(player, product)
    override fun checkAll(player: MenuViewer) = updateScreens.checkAll(player)
    override fun everyone(player: MenuViewer, done: String, change: (UpdateSnapshot) -> java.util.concurrent.CompletionStage<*>) =
        updateScreens.everyone(player, done, change)
    override fun groups(player: MenuViewer, page: Int): Unit = groupScreens.groups(player, page)
    override fun history(player: MenuViewer, product: String?, page: Int) = historyScreens.history(player, product, page)
    override fun hold(snapshot: UpdateSnapshot, duration: Duration?, player: MenuViewer): java.util.concurrent.CompletionStage<*> =
        updateScreens.hold(snapshot, duration, player)
    override fun home(player: MenuViewer) = homeScreens.home(player)
    override fun journal(player: MenuViewer, level: String?, page: Int) = journalScreens.journal(player, level, page)
    override fun journalRows(level: String?, back: (MenuViewer) -> Unit): List<Row> = journalScreens.journalRows(level, back)
    override fun labelled(label: String, text: String): List<Pair<String, String>> = taskScreens.labelled(label, text)
    override fun members(map: NetworkReport): List<NetworkServer> = networkScreens.members(map)
    override fun network(player: MenuViewer, page: Int) = networkScreens.network(player, page)
    override fun ownerLine(name: String): String = taskScreens.ownerLine(name)
    override fun ownerParameters(owner: OwnerInfo?): List<Pair<String, String>> = taskScreens.ownerParameters(owner)
    override fun plugins(player: MenuViewer, filter: Filter, page: Int) = updateScreens.plugins(player, filter, page)
    override fun setAutomatic(snapshot: UpdateSnapshot, enable: Boolean, player: MenuViewer): java.util.concurrent.CompletionStage<*> =
        updateScreens.setAutomatic(snapshot, enable, player)
    override fun settings(player: MenuViewer) = settingsScreens.settings(player)
    override fun stats(player: MenuViewer) = statsScreens.stats(player)
    override fun support(player: MenuViewer) = settingsScreens.support(player)
    override fun tasks(player: MenuViewer, period: TaskPeriod, state: TaskState, page: Int) = taskScreens.tasks(player, period, state, page)
    override fun tools(player: MenuViewer) = settingsScreens.tools(player)
    override fun versioned(owner: OwnerInfo): String = taskScreens.versioned(owner)

    fun open(player: MenuViewer) = session.away(player) { home(player) }
}
