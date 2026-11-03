package org.pnfolder.pnlibrary.core.commands

import net.kyori.adventure.text.event.ClickEvent
import org.pnfolder.pnlibrary.api.chat.ChatKit
import org.pnfolder.pnlibrary.api.chat.ChatNotice
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.chat.ChatReader
import org.pnfolder.pnlibrary.api.chat.ChatTone
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.common.text.RussianText
import org.pnfolder.pnlibrary.core.link.NetworkServer
import org.pnfolder.pnlibrary.core.link.ServerGroups

internal const val GROUPS_ROOT = "/pn groups"

internal fun groupsKit(context: CommandContext) = ChatKit(ChatReader.of(context.sender))

/** One message in the style of the other chat commands. */
internal fun groupsNotice(context: CommandContext, tone: ChatTone, text: String, detail: String? = null) {
    groupsKit(context).notice(ChatNotice(tone, text, detail)).forEach(context.sender::send)
}

/** The read-only pages of `/pn groups`: the list of groups, one group, one server in a group, and the groups of a server. */
internal class GroupsPages(private val groups: ServerGroups, private val servers: () -> List<NetworkServer>) {
    private val ROOT get() = GROUPS_ROOT

    private fun kit(context: CommandContext) = groupsKit(context)

    private fun notice(context: CommandContext, tone: ChatTone, text: String, detail: String? = null) =
        groupsNotice(context, tone, text, detail)

    fun of(context: CommandContext, server: String) {
        val kit = kit(context)
        val member = groups.of(server)
        val lines = mutableListOf(kit.blank(), kit.header(ServerGroups.label(server), kit.text("в группах", ChatPalette.LABEL)))
        if (member.isEmpty()) lines += kit.note("Сервер не состоит ни в одной группе.", ChatPalette.FAINT)
        member.forEach { group ->
            val wanted = group.wanted(server).joinToString(", ").ifEmpty { "нет плагинов" }
            var line = kit.text(" ").append(kit.text(group.name, ChatPalette.ACCENT)).append(kit.dot()).append(kit.text(wanted, ChatPalette.SOFT))
            if (kit.reader.interactive) {
                line = line.hoverEvent(kit.tooltip(kit.text("Открыть сервер в группе ›", ChatPalette.ACCENT)))
                    .clickEvent(ClickEvent.runCommand("$ROOT server ${group.name} $server"))
            }
            lines += line
        }
        lines.forEach(context.sender::send)
    }

    fun viewServer(context: CommandContext, name: String, server: String) {
        val kit = kit(context)
        val group = groups.get(name)
        if (group == null || group.servers.none { it.equals(server, true) }) {
            notice(context, ChatTone.FAILURE, "В группе $name нет сервера ${ServerGroups.label(server)}.", "Добавить: $ROOT join $name $server")
            return
        }
        val have = servers().firstOrNull { it.name.equals(server, true) }?.report?.plugins.orEmpty()
        val note = kit.text(if (group.customized(server)) "свои настройки" else "как у всех", ChatPalette.LABEL)
        val lines = mutableListOf(kit.blank(), kit.header("$name · ${ServerGroups.label(server)}", note))
        val all = (group.plugins + group.wanted(server)).sortedBy { it.lowercase() }
        if (all.isEmpty()) lines += kit.note("У группы нет плагинов: $ROOT require $name <плагин>", ChatPalette.FAINT)
        all.forEach { plugin ->
            val wanted = group.wanted(server).any { it.equals(plugin, true) }
            val mark = if (wanted) "нужен" else "не нужен"
            val common = group.plugins.any { it.equals(plugin, true) }
            val origin = if (wanted && !common) "только этому серверу" else if (!wanted) "убран у этого сервера" else "общий"
            val has = if (have.any { it.product.equals(plugin, true) }) "стоит" else "нет"
            lines += kit.text(" ").append(kit.text(plugin, ChatPalette.ACCENT)).append(kit.dot()).append(kit.text("$mark ($origin)", ChatPalette.SOFT))
                .append(kit.dot()).append(kit.text(has, if (has == "стоит") ChatPalette.ACCENT else ChatPalette.DANGER))
        }
        lines += kit.blank()
        lines += kit.commandHints(
            "$ROOT skip $name $server <плагин>" to "этому серверу плагин не нужен",
            "$ROOT extra $name $server <плагин>" to "этому серверу нужен ещё плагин",
            "$ROOT reset $name $server" to "сделать как у всех",
        )
        lines.forEach(context.sender::send)
    }

    fun list(context: CommandContext) {
        val kit = kit(context)
        val all = groups.all()
        val count = "${all.size} ${RussianText.plural(all.size.toLong(), "группа", "группы", "групп")}"
        val lines = mutableListOf(kit.blank(), kit.header("Группы серверов", kit.text(count, ChatPalette.LABEL)))
        if (all.isEmpty()) lines += kit.note("Групп пока нет. Создайте: $ROOT create <имя>", ChatPalette.FAINT)
        all.forEach { group ->
            val states = groups.compare(group, servers())
            val bad = states.count { !it.ok }
            var line = kit.text(" ").append(kit.text(kit.glyph('●'), if (bad == 0) ChatPalette.ACCENT else ChatPalette.DANGER))
                .append(kit.text(" ${group.name}", ChatPalette.ACCENT))
                .append(kit.dot()).append(kit.text("серверов: ${group.servers.size}", ChatPalette.SOFT))
                .append(kit.dot()).append(kit.text("плагинов: ${group.plugins.size}", ChatPalette.SOFT))
                .append(kit.dot()).append(
                    if (group.servers.isEmpty()) kit.text("пусто", ChatPalette.FAINT)
                    else if (bad == 0) kit.text("всё в порядке", ChatPalette.ACCENT)
                    else kit.text("требуют внимания: $bad", ChatPalette.DANGER),
                )
            if (kit.reader.interactive) {
                line = line.hoverEvent(kit.tooltip(kit.text("Нажмите, чтобы открыть группу ›", ChatPalette.ACCENT)))
                    .clickEvent(ClickEvent.runCommand("$ROOT info ${group.name}"))
            }
            lines += line
        }
        if (!kit.reader.interactive) {
            lines += kit.blank()
            lines += kit.commandHints(
                "$ROOT info <группа>" to "состояние группы",
                "$ROOT create <группа>" to "новая группа",
                "$ROOT join <группа> <сервер>" to "добавить сервер",
                "$ROOT require <группа> <плагин>" to "плагин для всех серверов группы",
                "$ROOT server <группа> <сервер>" to "свои настройки одного сервера",
                "$ROOT of <сервер>" to "в каких группах сервер",
            )
        }
        lines.forEach(context.sender::send)
    }

    fun view(context: CommandContext, name: String) {
        val kit = kit(context)
        val group = groups.get(name)
        if (group == null) {
            notice(context, ChatTone.FAILURE, "Нет группы $name.", "Список: $ROOT")
            return
        }
        val lines = mutableListOf(kit.blank(), kit.header(group.name, kit.text("группа серверов", ChatPalette.LABEL)))
        lines += kit.field("Плагины", kit.text(group.plugins.joinToString(", ").ifEmpty { "не заданы" }, ChatPalette.TEXT))
        lines += kit.blank()
        lines += kit.section("Серверы")
        if (group.servers.isEmpty()) lines += kit.note("Серверов нет: $ROOT join ${group.name} <сервер>", ChatPalette.FAINT)
        groups.compare(group, servers()).forEach { state ->
            val verdict = when {
                state.known == null -> kit.text("не присылал отчёт", ChatPalette.DANGER)
                !state.known.online -> kit.text("нет связи", ChatPalette.DANGER)
                state.ok -> kit.text("всё в порядке", ChatPalette.ACCENT)
                else -> kit.text("", ChatPalette.TEXT)
            }
            var line = kit.text(" ").append(kit.text(kit.glyph('●'), if (state.ok) ChatPalette.ACCENT else ChatPalette.DANGER))
                .append(kit.text(" ${ServerGroups.label(state.server)}  ", ChatPalette.ACCENT)).append(verdict)
                .let { if (state.customized) it.append(kit.text("свои настройки  ", ChatPalette.LABEL)) else it }
            if (kit.reader.interactive) {
                line = line.hoverEvent(kit.tooltip(kit.text("Настройки сервера в группе ›", ChatPalette.ACCENT)))
                    .clickEvent(ClickEvent.runCommand("$ROOT server ${group.name} ${state.server}"))
            }
            if (state.missing.isNotEmpty()) line = line.append(kit.text("нет: ${state.missing.joinToString(", ")}  ", ChatPalette.DANGER))
            if (state.outdated.isNotEmpty()) line = line.append(kit.text("можно обновить: ${state.outdated.joinToString(", ")}", ChatPalette.SOFT))
            lines += line
        }
        lines += kit.blank()
        lines += kit.actions(
            kit.button("[Проверить]", "$ROOT check ${group.name}", "Каждый сервер группы проверит обновления", ChatPalette.ACCENT),
            kit.button("[Обновить]", "$ROOT update ${group.name}", "Скачать обновления плагинов группы на всех серверах", ChatPalette.ACCENT),
            kit.button("[К списку]", ROOT, "Все группы", ChatPalette.SOFT),
        ) ?: kit.blank()
        lines.forEach(context.sender::send)
    }
}
