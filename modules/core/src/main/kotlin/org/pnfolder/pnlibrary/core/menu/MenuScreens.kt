package org.pnfolder.pnlibrary.core.menu

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.chat.ChatTone

/** The helpers every screen group uses; they live in the [MenuSession] and are only forwarded here. */
internal abstract class MenuScreens(private val session: MenuSession, protected val routes: MenuRoutes) {
    protected val data get() = session.data
    protected val host get() = session.host
    protected val remote get() = session.remote

    protected fun go(action: (MenuViewer) -> Unit) = session.go(action)

    protected fun goClick(action: (MenuViewer, ClickType) -> Unit) = session.goClick(action)

    protected fun away(player: MenuViewer? = null, action: () -> Unit) = session.away(player, action)

    protected fun sync(action: () -> Unit) = session.sync(action)

    protected fun later(delayMillis: Long, action: () -> Unit) = session.later(delayMillis, action)

    protected fun runCommand(player: MenuViewer, line: String) = session.runCommand(player, line)

    protected fun show(player: MenuViewer, menu: Menu) = session.show(player, menu)

    protected fun allowed(player: MenuViewer, permission: String, action: () -> Unit) = session.allowed(player, permission, action)

    protected fun deliver(player: MenuViewer, lines: List<Component>) = session.deliver(player, lines)

    protected fun tell(player: MenuViewer, tone: ChatTone, text: String, detail: String? = null) =
        session.tell(player, tone, text, detail)

    protected fun list(
        player: MenuViewer,
        title: String,
        rows: List<Row>,
        page: Int,
        perPage: Int,
        reopen: (MenuViewer, Int) -> Unit,
        back: (MenuViewer) -> Unit,
        emptyText: String,
        total: Int = rows.size,
        extra: (MenuBuilder) -> Unit = {},
    ) = session.list(player, title, rows, page, perPage, reopen, back, emptyText, total, extra)

    protected fun snapshots() = session.snapshots()

    protected fun connectedCount() = session.connectedCount()
}
