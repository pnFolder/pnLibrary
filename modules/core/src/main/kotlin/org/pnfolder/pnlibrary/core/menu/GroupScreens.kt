package org.pnfolder.pnlibrary.core.menu

import org.pnfolder.pnlibrary.api.chat.ChatKit
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.chat.ChatReader
import org.pnfolder.pnlibrary.api.chat.ChatTone
import org.pnfolder.pnlibrary.core.link.LinkNames
import org.pnfolder.pnlibrary.core.link.NetworkReport
import org.pnfolder.pnlibrary.core.link.RemoteAction
import org.pnfolder.pnlibrary.core.link.ServerGroups

internal class GroupScreens(session: MenuSession, routes: MenuRoutes) : MenuScreens(session, routes) {
    private val comparer = ServerGroups { null }

    /** The groups live on the proxy; this asks it from a menu that is not its own. */
    private val proxyData by lazy { RemoteMenuData(LinkNames.PROXY, host.link.data) }

    /** The groups of servers the proxy keeps; a click opens one group. */
    fun groups(player: MenuViewer, page: Int): Unit = withGroups(player) { all, map ->
        val rows = all.map { group ->
            val bad = comparer.compare(group, routes.members(map)).count { !it.ok }
            val material = if (group.servers.isEmpty()) "CHEST" else if (bad == 0) "EMERALD_BLOCK" else "REDSTONE_BLOCK"
            val parameters = listOf(
                "Серверов" to "${group.servers.size}",
                "Плагинов" to "${group.plugins.size}",
                "Состояние" to if (bad == 0) "✔ в порядке" else "✖ требуют внимания: $bad",
            )
            val item = icon(Icons.OVERVIEW, material, group.name, Style.lore(emptyList(), parameters, Style.left("открыть")))
            Row(item, go { who -> group(who, group.name) })
        }
        list(
            player, "Группы", rows, page, PAGE_SIZE, { who, to -> groups(who, to) }, { routes.network(it, 0) }, "Групп нет",
            extra = {
                val create = icon(
                    Icons.PLUGIN, "WRITABLE_BOOK", "Новая группа",
                    Style.lore(
                        listOf("Группа — набор серверов с общим списком плагинов.", "Имя пишется в чате: любое, без пробелов."),
                        emptyList(), Style.left("создать"),
                    ), good = true,
                )
                it.button(GROUP_ADD, create, go { who -> suggest(who, "Назовите группу и нажмите Enter:", "/pnproxy groups create ") })
            },
        )
    }

    /** Reads the groups from the proxy away from the server thread, then runs [then] on it with them and the last network map. */
    private fun withGroups(player: MenuViewer, then: (List<ServerGroups.Group>, NetworkReport) -> Unit) {
        val map = host.link.network?.report
        if (map == null) {
            routes.home(player)
            return
        }
        host.async {
            try {
                val loaded = proxyData.groups()
                host.sync { then(loaded, map) }
            } catch (error: Exception) {
                host.sync { tell(player, ChatTone.FAILURE, "Прокси не ответил.", reason(error)) }
            }
        }
    }

    /** Asks the proxy for one change of the groups when [player] may, then runs [then]. */
    private fun editGroup(player: MenuViewer, edit: GroupEdit, then: () -> Unit) {
        allowed(player, GROUPS_PERMISSION) {
            host.async {
                try {
                    val done = proxyData.editGroup(edit)
                    host.sync { if (done) then() else tell(player, ChatTone.FAILURE, "Не получилось изменить группу ${edit.group}.") }
                } catch (error: Exception) {
                    host.sync { tell(player, ChatTone.FAILURE, "Прокси не ответил.", reason(error)) }
                }
            }
        }
    }

    /** Puts [command] in the chat line of [player] to finish, with a click on the message. */
    private fun suggest(player: MenuViewer, text: String, command: String) {
        player.close()
        val kit = ChatKit(ChatReader.PLAYER)
        val line = kit.mark(ChatTone.INFO).append(kit.text(text, ChatPalette.TEXT)).append(kit.text(" "))
            .append(kit.bracketed("Ввести команду", ChatPalette.ACCENT))
            .clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand(command))
            .hoverEvent(kit.tooltip(kit.text("Нажмите, чтобы вставить в чат", ChatPalette.TEXT), kit.text(command.trim(), ChatPalette.LABEL)))
        player.send(listOf(line))
    }

    /** One group: each server with what it lacks, and the buttons that change the group or ask all of its servers at once. */
    private fun group(player: MenuViewer, name: String): Unit = withGroups(player) { all, map ->
        val group = all.firstOrNull { it.name == name }
        if (group == null) {
            groups(player, 0)
            return@withGroups
        }
        val states = comparer.compare(group, routes.members(map))
        val rows = states.map { serverRow(name, it) }
        list(
            player, "Группа ${group.name}", rows, 0, PAGE_SIZE, { who, _ -> group(who, name) }, { groups(it, 0) }, "Серверов нет",
            extra = { groupButtons(it, name, group, states) },
        )
    }

    /** One server of a group as a row: green when nothing is missing; left click opens its settings, right click removes it. */
    private fun serverRow(name: String, state: ServerGroups.ServerState): Row {
        val material = if (state.ok) "EMERALD_BLOCK" else "REDSTONE_BLOCK"
        val parameters = buildList {
            add("На связи" to if (state.known?.online == true) "✔ да" else "✖ нет")
            add("Свои настройки" to if (state.customized) "есть" else "нет, как у всех")
            if (state.missing.isNotEmpty()) add("Нет плагинов" to state.missing.joinToString(", "))
            if (state.outdated.isNotEmpty()) add("Можно обновить" to state.outdated.joinToString(", "))
        }
        val hints = arrayOf(Style.left("настроить сервер"), Style.right("убрать из группы"))
        val click = goClick { who, how ->
            if (how == ClickType.RIGHT) editGroup(who, GroupEdit(GroupOp.REMOVE_SERVER, name, state.server)) { group(who, name) }
            else groupServer(who, name, state.server)
        }
        val lore = Style.lore(emptyList(), parameters, *hints)
        return Row(icon(Icons.OVERVIEW, material, ServerGroups.label(state.server), lore, muted = state.known == null), click)
    }

    private fun groupButtons(builder: MenuBuilder, name: String, group: ServerGroups.Group, states: List<ServerGroups.ServerState>) {
        val check = icon(
            Icons.CHECK, "COMPASS", "Проверить всех",
            Style.lore(
                listOf("Серверы группы проверят, вышли ли обновления.", "Ничего не скачивается."),
                emptyList(), Style.left("проверить"),
            ),
        )
        builder.button(NETWORK_CHECK, check, go { who -> allowed(who, RemoteAction.CHECK.permission) { askGroup(who, states, RemoteAction.CHECK) } })
        val update = icon(
            Icons.PLUGIN, "GOLD_INGOT", "Обновить всех",
            Style.lore(
                listOf("Серверы группы скачают найденные обновления.", "Они применятся после перезапуска."),
                emptyList(), Style.left("скачать"),
            ),
        )
        builder.button(NETWORK_UPDATE, update, go { who -> allowed(who, RemoteAction.UPDATE.permission) { askGroup(who, states, RemoteAction.UPDATE) } })
        val servers = icon(
            Icons.OVERVIEW, "LIME_DYE", "Добавить сервер",
            Style.lore(
                listOf("Добавляет сервер или прокси в группу.", "Сервер может состоять в нескольких группах."),
                emptyList(), Style.left("выбрать"),
            ), good = true,
        )
        builder.button(GROUP_ADD, servers, go { who -> pickServer(who, name, 0) })
        val plugins = icon(
            Icons.PLUGIN, "BOOKSHELF", "Плагины группы",
            Style.lore(
                listOf("Плагины, нужные каждому серверу группы: ${group.plugins.size}.", "Если плагина нет, сервер будет в красном."),
                emptyList(), Style.left("открыть"),
            ),
        )
        builder.button(GROUP_PLUGINS, plugins, go { who -> groupPlugins(who, name, 0) })
        val delete = icon(
            Icons.PROBLEMS, "TNT", "Удалить группу",
            Style.lore(
                listOf("Удаляется только список группы.", "Серверы и их плагины не трогаются."),
                emptyList(), Style.shiftLeft("удалить"),
            ), danger = true,
        )
        builder.button(GROUP_DELETE, delete, goClick { who, how ->
            if (how == ClickType.SHIFT_LEFT) editGroup(who, GroupEdit(GroupOp.DELETE, name)) { groups(who, 0) }
            else tell(who, ChatTone.PROGRESS, "Чтобы удалить группу, нажмите с Shift.")
        })
    }

    /** Servers of the network that are not in the group yet; a click puts one in. */
    private fun pickServer(player: MenuViewer, name: String, page: Int): Unit = withGroups(player) { all, map ->
        val group = all.firstOrNull { it.name == name }
        if (group == null) {
            groups(player, 0)
            return@withGroups
        }
        val rows = routes.members(map).filter { server -> group.servers.none { it.equals(server.name, true) } }.map { server ->
            val others = all.filter { other -> other !== group && other.servers.any { it.equals(server.name, true) } }.map { it.name }
            val parameters = listOf("Уже в группах" to others.joinToString(", ").ifEmpty { "нет" })
            val hint = Style.left("добавить в группу")
            val item = icon(Icons.OVERVIEW, "EMERALD_BLOCK", ServerGroups.label(server.name), Style.lore(emptyList(), parameters, hint))
            Row(item, go { who -> editGroup(who, GroupEdit(GroupOp.ADD_SERVER, name, server.name)) { group(who, name) } })
        }
        list(player, "Добавить в $name", rows, page, PAGE_SIZE, { who, to -> pickServer(who, name, to) }, { group(it, name) }, "Все серверы уже в группе")
    }

    /** The plugins every server of the group has; a click takes one away from all. */
    private fun groupPlugins(player: MenuViewer, name: String, page: Int): Unit = withGroups(player) { all, _ ->
        val group = all.firstOrNull { it.name == name }
        if (group == null) {
            groups(player, 0)
            return@withGroups
        }
        val rows = group.plugins.sortedBy { it.lowercase() }.map { plugin ->
            val item = icon(Icons.PLUGIN, "BOOK", plugin, Style.lore(emptyList(), emptyList(), Style.left("убрать у всех серверов группы")))
            Row(item, go { who -> editGroup(who, GroupEdit(GroupOp.REMOVE_PLUGIN, name, plugin = plugin)) { groupPlugins(who, name, page) } })
        }
        list(
            player, "Плагины $name", rows, page, PAGE_SIZE, { who, to -> groupPlugins(who, name, to) }, { group(it, name) }, "Плагинов нет",
            extra = {
                val add = icon(
                    Icons.PLUGIN, "LIME_DYE", "Добавить плагин",
                    Style.lore(listOf("Плагин станет обязательным", "для каждого сервера группы."), emptyList(), Style.left("выбрать")), good = true,
                )
                it.button(GROUP_ADD, add, go { who -> pickPlugin(who, name, null, 0) })
            },
        )
    }

    /** The plugins the servers report, which the group or [server] does not ask for yet; a click asks for one. */
    private fun pickPlugin(player: MenuViewer, name: String, server: String?, page: Int): Unit = withGroups(player) { all, map ->
        val group = all.firstOrNull { it.name == name }
        if (group == null) {
            groups(player, 0)
            return@withGroups
        }
        val have = if (server == null) group.plugins else group.wanted(server)
        val known = (map.servers.map { it.report } + map.proxy).flatMap { it.plugins }.map { it.product }.distinct().sortedBy { it.lowercase() }
        val rows = known.filter { plugin -> have.none { it.equals(plugin, true) } }.map { plugin ->
            val hint = Style.left(if (server == null) "нужен всем" else "нужен этому серверу")
            val item = icon(Icons.PLUGIN, "BOOK", plugin, Style.lore(emptyList(), emptyList(), hint))
            val edit = if (server == null) GroupEdit(GroupOp.ADD_PLUGIN, name, plugin = plugin) else GroupEdit(GroupOp.NEED, name, server, plugin)
            Row(item, go { who -> editGroup(who, edit) { if (server == null) groupPlugins(who, name, 0) else groupServer(who, name, server) } })
        }
        val back: (MenuViewer) -> Unit = { who -> if (server == null) groupPlugins(who, name, 0) else groupServer(who, name, server) }
        list(player, "Добавить плагин", rows, page, PAGE_SIZE, { who, to -> pickPlugin(who, name, server, to) }, back, "Все известные плагины уже есть")
    }

    /** One server of a group: a click switches whether it needs a plugin, whatever the other servers do. */
    private fun groupServer(player: MenuViewer, name: String, server: String): Unit = withGroups(player) { all, map ->
        val group = all.firstOrNull { it.name == name }
        if (group == null || group.servers.none { it.equals(server, true) }) {
            group(player, name)
            return@withGroups
        }
        val have = routes.members(map).firstOrNull { it.name.equals(server, true) }?.report?.plugins.orEmpty()
        val names = (group.plugins + group.wanted(server)).sortedBy { it.lowercase() }
        val rows = names.map { plugin -> serverPluginRow(name, server, group, plugin, have.any { it.product.equals(plugin, true) }) }
        list(
            player, "$name · ${ServerGroups.label(server)}", rows, 0, PAGE_SIZE, { who, _ -> groupServer(who, name, server) },
            { group(it, name) }, "У группы нет плагинов",
            extra = { serverButtons(it, name, server) },
        )
    }

    private fun serverPluginRow(name: String, server: String, group: ServerGroups.Group, plugin: String, present: Boolean): Row {
        val wanted = group.wanted(server).any { it.equals(plugin, true) }
        val common = group.plugins.any { it.equals(plugin, true) }
        val material = when {
            !wanted -> "GRAY_DYE"
            present -> "EMERALD_BLOCK"
            else -> "REDSTONE_BLOCK"
        }
        val parameters = listOf(
            "Нужен серверу" to if (wanted) "✔ да" else "✖ нет",
            "Откуда" to if (!wanted) "убран только у этого сервера" else if (common) "общий для группы" else "только этому серверу",
            "На сервере" to if (present) "✔ стоит" else "✖ нет",
        )
        val hint = Style.left(if (wanted) "не нужен этому серверу" else "нужен этому серверу")
        val item = icon(Icons.PLUGIN, material, plugin, Style.lore(emptyList(), parameters, hint), muted = !wanted)
        val op = if (wanted) GroupOp.SKIP else GroupOp.NEED
        return Row(item, go { who -> editGroup(who, GroupEdit(op, name, server, plugin)) { groupServer(who, name, server) } })
    }

    private fun serverButtons(builder: MenuBuilder, name: String, server: String) {
        val add = icon(
            Icons.PLUGIN, "LIME_DYE", "Ещё плагин этому серверу",
            Style.lore(
                listOf("Плагин нужен только этому серверу,", "остальные серверы группы его не ждут."),
                emptyList(), Style.left("выбрать"),
            ), good = true,
        )
        builder.button(GROUP_ADD, add, go { who -> pickPlugin(who, name, server, 0) })
        val reset = icon(
            Icons.CHECK, "COMPASS", "Как у всех",
            Style.lore(
                listOf("Сервер снова ждёт то же, что и вся группа.", "Его особые настройки удаляются."),
                emptyList(), Style.left("сбросить"),
            ),
        )
        builder.button(GROUP_PLUGINS, reset, go { who -> editGroup(who, GroupEdit(GroupOp.RESET, name, server)) { groupServer(who, name, server) } })
    }

    private fun askGroup(player: MenuViewer, states: List<ServerGroups.ServerState>, action: RemoteAction) {
        var sent = 0
        states.filter { it.server != ServerGroups.PROXY }.forEach { state ->
            val products = if (action == RemoteAction.UPDATE) state.outdated else listOf(null)
            products.forEach { product ->
                val label = product?.let { "${ServerGroups.label(state.server)} · $it" } ?: ServerGroups.label(state.server)
                val error = host.link.control.send(state.server, action, product, player.name) { result ->
                    sync {
                        if (player.online) tell(player, if (result.ok) ChatTone.SUCCESS else ChatTone.FAILURE, "$label: ${result.text}")
                    }
                }
                if (error == null) sent++ else tell(player, ChatTone.FAILURE, "$label: $error")
            }
        }
        tell(player, ChatTone.PROGRESS, if (sent == 0) "Делать нечего." else "Запросов отправлено: $sent.")
    }
}
