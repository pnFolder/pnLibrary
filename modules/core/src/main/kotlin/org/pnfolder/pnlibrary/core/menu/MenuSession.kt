package org.pnfolder.pnlibrary.core.menu

import org.pnfolder.pnlibrary.api.chat.ChatKit
import org.pnfolder.pnlibrary.api.chat.ChatNotice
import org.pnfolder.pnlibrary.api.chat.ChatReader
import org.pnfolder.pnlibrary.api.chat.ChatTone
import org.pnfolder.pnlibrary.api.updates.UpdateSnapshot
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateCatalogView

internal class MenuSession(
    val data: MenuData,
    val host: MenuHost,
    private val remoteName: String? = null,
) {
    val remote get() = remoteName != null

    fun allowed(player: MenuViewer, permission: String, action: () -> Unit) {
        if (player.hasPermission(permission) || player.hasPermission("pnlibrary.admin")) action()
        else tell(player, ChatTone.FAILURE, "Не хватает права.", permission)
    }

    fun deliver(player: MenuViewer, lines: List<net.kyori.adventure.text.Component>) = player.send(lines)

    fun tell(player: MenuViewer, tone: ChatTone, text: String, detail: String? = null) {
        val lines = UpdateCatalogView.notice(ChatNotice(tone, text, detail), ROUTES)
        deliver(player, lines)
    }

    /**
     * A page of a list. [rows] are the rows of [page] when [total] says the list is larger than [rows], otherwise
     * the whole list that is cut into pages here. [reopen] receives the player and the page to open.
     */
    fun list(
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
    ) {
        val pages = ((total - 1) / perPage + 1).coerceAtLeast(1)
        val current = page.coerceIn(0, pages - 1)
        val shown = if (total == rows.size) rows.drop(current * perPage).take(perPage) else rows.take(perPage)
        val builder = Menus.chest(title("$title ${current + 1}/$pages")).rows(LIST_ROWS)
        shown.forEachIndexed { slot, row -> if (row.click != null) builder.button(slot, row.stack, row.click) else builder.slot(slot, row.stack) }
        if (total == 0) builder.slot(22, icon(Icons.PROBLEMS, "BARRIER", emptyText, emptyList(), muted = true))
        footerCorners(builder)
        builder.fill(pane(), 0, FOOTER_END)
        builder.button(BACK, backItem(), go { back(it) })
        if (current > 0) {
            val previous = icon(Icons.BACK, "ARROW", "Назад по списку", Style.lore(emptyList(), emptyList(), Style.left("страница $current")), good = true)
            builder.button(PREVIOUS, previous, go { reopen(it, current - 1) })
        }
        if (current < pages - 1) {
            val following = icon(Icons.NEXT, "ARROW", "Дальше по списку",
                Style.lore(emptyList(), emptyList(), Style.left("страница ${current + 2}")), good = true)
            builder.button(NEXT, following, go { reopen(it, current + 1) })
        }
        builder.button(
            REFRESH,
            icon(Icons.CHECK, "COMPASS", "Обновить список", Style.lore(listOf("Перечитать данные заново."), emptyList(), Style.left("обновить"))),
            go { reopen(it, current) },
        )
        extra(builder)
        builder.button(CLOSE, closeItem(), go { it.close() })
        show(player, builder.build())
    }

    fun go(action: (MenuViewer) -> Unit) = MenuClickHandler { click -> away(click.player) { action(click.player) } }

    fun goClick(action: (MenuViewer, ClickType) -> Unit) = MenuClickHandler { click -> away(click.player) { action(click.player, click.click) } }

    /** Runs [action] where it may wait for the data: here at once, for another server away from the server thread. */
    fun away(player: MenuViewer? = null, action: () -> Unit) {
        if (!remote) return action()
        host.async {
            try {
                action()
            } catch (error: Exception) {
                if (player != null) tell(player, ChatTone.FAILURE, "Сервер $remoteName не ответил.", reason(error))
            }
        }
    }

    fun sync(action: () -> Unit) = if (remote) away(null, action) else host.sync(action)

    fun later(delayMillis: Long, action: () -> Unit) = host.later(delayMillis) { away(null, action) }

    fun runCommand(player: MenuViewer, line: String) {
        if (!remote) return player.runCommand(line)
        away(player) {
            val viewer = ViewerInfo(player.name, MenuPermissions.ALL.filter(player::hasPermission).toSet())
            val lines = try {
                data.command(viewer, line)
            } catch (error: Exception) {
                ChatKit(ChatReader.PLAYER).notice(ChatNotice(ChatTone.FAILURE, "Команда не выполнилась: /$line", error.message ?: error.javaClass.simpleName))
            }
            player.send(lines)
        }
    }

    fun show(player: MenuViewer, menu: Menu) = player.open(menu)

    fun snapshots(): List<UpdateSnapshot> = data.snapshots()

    fun connectedCount(): Int {
        val names = snapshots().map { it.product.lowercase() }.toSet()
        return names.size + data.plugins().count { it.name.lowercase() !in names }
    }
}
