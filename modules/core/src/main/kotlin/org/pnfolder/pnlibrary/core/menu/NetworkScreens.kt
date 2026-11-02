package org.pnfolder.pnlibrary.core.menu

import org.pnfolder.pnlibrary.api.chat.ChatTone
import org.pnfolder.pnlibrary.api.updates.UpdateState
import org.pnfolder.pnlibrary.core.link.LinkHub
import org.pnfolder.pnlibrary.core.link.LinkNames
import org.pnfolder.pnlibrary.core.link.NetworkReport
import org.pnfolder.pnlibrary.core.link.NetworkServer
import org.pnfolder.pnlibrary.core.link.ServerGroups
import org.pnfolder.pnlibrary.core.updates.catalog.CatalogLabels

internal class NetworkScreens(session: MenuSession, routes: MenuRoutes) : MenuScreens(session, routes) {
    /** The servers of the network as the proxy last described them; a click opens one server. */
    fun network(player: MenuViewer, page: Int) {
        val received = LinkHub.network
        if (received == null) {
            routes.home(player)
            tell(player, ChatTone.INFO, "Прокси ещё не прислал карту сети.", "Она приходит, когда на сервере есть игрок. Попробуйте через минуту.")
            return
        }
        val map = received.report
        val proxy = NetworkServer(PROXY_LABEL, true, map.proxy)
        val rows = (listOf(proxy) + map.servers).map { server -> Row(serverItem(server), go { who -> openServer(who, server, page) }) }
        list(
            player, "Сеть", rows, page, PAGE_SIZE, { who, to -> network(who, to) }, { routes.home(it) }, "Серверов нет",
            extra = {
                val about = "Серверы с общим набором плагинов; у каждого сервера могут быть свои отличия."
                val item = section(Icons.OVERVIEW, "BOOKSHELF", "Группы", about, "Групп" to "${map.groups.size}")
                it.button(NETWORK_CHECK, item, go { who -> routes.groups(who, 0) })
            },
        )
    }

    /** The proxy as a member of groups, then the servers. */
    fun members(map: NetworkReport): List<NetworkServer> = listOf(NetworkServer(ServerGroups.PROXY, true, map.proxy)) + map.servers

    private fun serverItem(server: NetworkServer): ItemSpec {
        val report = server.report
        val updates = report.plugins.count { it.state == UpdateState.UPDATE_AVAILABLE.name }
        val problems = report.plugins.count { runCatching { UpdateState.valueOf(it.state) }.getOrNull() in CatalogLabels.PROBLEMS }
        val parameters = listOf(
            "Сервер" to report.platform,
            "pnLibrary" to report.libraryVersion,
            "Игроков" to "${report.players}",
            "Плагинов" to "${report.plugins.size}",
            "Обновлений" to if (updates > 0) "✦ $updates" else "✔ нет",
            "Проблем" to if (problems > 0) "✖ $problems" else "✔ нет",
            "На связи" to if (server.online) "✔ да" else "✖ нет",
        )
        val material = when {
            !server.online -> "BARRIER"
            problems > 0 -> "REDSTONE_BLOCK"
            updates > 0 -> "GOLD_BLOCK"
            else -> "EMERALD_BLOCK"
        }
        return icon(Icons.OVERVIEW, material, server.name, Style.lore(emptyList(), parameters, Style.left("открыть")), muted = !server.online)
    }

    /** Opens the menu of [server]: a copy of its own menu, or the short page the proxy gets, which has no menu of its own. */
    private fun openServer(player: MenuViewer, server: NetworkServer, backPage: Int) {
        val target = if (server.name == PROXY_LABEL) LinkNames.PROXY else server.name
        if (!host.openRemote(player, target)) tell(player, ChatTone.FAILURE, "Некому передать запрос: на ${server.name} нет игроков онлайн.")
    }
}
