package org.pnfolder.pnlibrary.core.menu

import org.pnfolder.pnlibrary.api.updates.UpdateSnapshot
import org.pnfolder.pnlibrary.core.commands.TaskPeriod
import org.pnfolder.pnlibrary.core.commands.TaskState
import org.pnfolder.pnlibrary.core.link.NetworkReport
import org.pnfolder.pnlibrary.core.link.NetworkServer
import java.time.Duration

/** The screens that more than one part of the menu opens, so each part needs no knowledge of the others. */
internal interface MenuRoutes {
    fun aboutOf(name: String): EventAbout?
    fun backups(player: MenuViewer, page: Int)
    fun busDays(player: MenuViewer, event: String?, page: Int)
    fun busEvent(player: MenuViewer, name: String, back: (MenuViewer) -> Unit)
    fun busHome(player: MenuViewer)
    fun busTraces(player: MenuViewer, event: String?, page: Int)
    fun card(player: MenuViewer, product: String)
    fun checkAll(player: MenuViewer)
    fun everyone(player: MenuViewer, done: String, change: (UpdateSnapshot) -> java.util.concurrent.CompletionStage<*>)
    fun groups(player: MenuViewer, page: Int): Unit
    fun history(player: MenuViewer, product: String?, page: Int)
    fun hold(snapshot: UpdateSnapshot, duration: Duration?, player: MenuViewer): java.util.concurrent.CompletionStage<*>
    fun home(player: MenuViewer)
    fun journal(player: MenuViewer, level: String?, page: Int)
    fun journalRows(level: String?, back: (MenuViewer) -> Unit): List<Row>
    fun labelled(label: String, text: String): List<Pair<String, String>>
    fun members(map: NetworkReport): List<NetworkServer>
    fun network(player: MenuViewer, page: Int)
    fun ownerLine(name: String): String
    fun ownerParameters(owner: OwnerInfo?): List<Pair<String, String>>
    fun plugins(player: MenuViewer, filter: Filter, page: Int)
    fun setAutomatic(snapshot: UpdateSnapshot, enable: Boolean, player: MenuViewer): java.util.concurrent.CompletionStage<*>
    fun settings(player: MenuViewer)
    fun stats(player: MenuViewer)
    fun support(player: MenuViewer)
    fun tasks(player: MenuViewer, period: TaskPeriod, state: TaskState, page: Int)
    fun tools(player: MenuViewer)
    fun versioned(owner: OwnerInfo): String
}
