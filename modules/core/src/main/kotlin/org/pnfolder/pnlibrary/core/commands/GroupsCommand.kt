package org.pnfolder.pnlibrary.core.commands

import net.kyori.adventure.text.event.ClickEvent
import org.pnfolder.pnlibrary.api.chat.ChatKit
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.chat.ChatReader
import org.pnfolder.pnlibrary.api.commands.ArgumentType
import org.pnfolder.pnlibrary.api.commands.CommandBuilder
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.CommandNodeBuilder
import org.pnfolder.pnlibrary.api.commands.command
import org.pnfolder.pnlibrary.common.text.RussianText
import org.pnfolder.pnlibrary.core.link.NetworkServer
import org.pnfolder.pnlibrary.core.link.RemoteAction
import org.pnfolder.pnlibrary.core.link.RemoteControl
import org.pnfolder.pnlibrary.core.link.ServerGroups

/**
 * `/pn groups` on a proxy: groups of servers and the plugins each of them should have, compared with what the
 * servers reported. A group is for answering "does every grief server have its plugins, and are they current".
 *
 * Every command starts with a word and then names the group, so a group may be called anything, even `create`.
 * A server may be in any number of groups, and inside a group one server may differ from the rest. The proxy itself
 * is a member under the name `@proxy`, which no server can have.
 *
 * | Command | What it does |
 * |---|---|
 * | `/pn groups` | the groups and how many of their servers are in order |
 * | `/pn groups info <group>` | each server of the group: what is missing and what can be updated |
 * | `/pn groups create <group>`, `delete <group>` | makes or deletes a group |
 * | `/pn groups join <group> <server>`, `leave <group> <server>` | puts a server in the group or takes it out |
 * | `/pn groups require <group> <plugin>`, `drop <group> <plugin>` | every server of the group has the plugin, or no longer has to |
 * | `/pn groups server <group> <server>` | what the group asks of one server and how it differs from the rest |
 * | `/pn groups skip <group> <server> <plugin>`, `extra <group> <server> <plugin>` | this server does not need a plugin, or needs one more |
 * | `/pn groups reset <group> <server>` | this server goes back to what the rest have |
 * | `/pn groups of <server>` | the groups a server is in |
 * | `/pn groups check <group>`, `update <group>` | every server of the group looks for updates, or downloads them |
 *
 * @param groups the stored groups
 * @param servers the servers to compare with
 */
class GroupsCommand(private val groups: ServerGroups, private val servers: () -> List<NetworkServer>) {
    /** Builds the command; it is mounted under `/pn` as `groups`. */
    fun definition(): CommandDefinition = command("groups") {
        permission(PERMISSION, "pnlibrary.admin")
        executes(::list)
        literal("create") {
            argument("group", ArgumentType.string()) { executes { create(it, it.get<String>("group")) } }
        }
        withGroup("delete") { executes { delete(it, it.get<String>("group")) } }
        withGroup("info") { executes { view(it, it.get<String>("group")) } }
        withGroup("join") { valued(true, ServerGroups.Group::addServer) }
        withGroup("leave") { valued(true, ServerGroups.Group::removeServer) }
        withGroup("require") { valued(false, ServerGroups.Group::addPlugin) }
        withGroup("drop") { valued(false, ServerGroups.Group::removePlugin) }
        withGroup("check") {
            permission(RemoteAction.CHECK.permission)
            executes { ask(it, RemoteAction.CHECK) }
        }
        withGroup("update") {
            permission(RemoteAction.UPDATE.permission)
            executes { ask(it, RemoteAction.UPDATE) }
        }
        withGroup("server") { withServer { executes { viewServer(it, it.get<String>("group"), it.get<String>("server")) } } }
        withGroup("skip") { withServer { override(false) } }
        withGroup("extra") { withServer { override(true) } }
        withGroup("reset") {
            withServer {
                executes { context ->
                    val server = context.get<String>("server")
                    val done = "Сервер ${ServerGroups.label(server)} в группе теперь как все."
                    say(context, groups.edit(context.get<String>("group")) { it.reset(server) }, done)
                }
            }
        }
        literal("of") {
            argument("server", ArgumentType.string()) {
                suggests { serverNames() }
                executes { of(it, it.get<String>("server")) }
            }
        }
    }

    /** The word [word], then the name of a group. */
    private fun CommandBuilder.withGroup(word: String, inside: CommandNodeBuilder.() -> Unit) {
        literal(word) {
            argument("group", ArgumentType.string()) {
                suggests { groups.all().map { it.name } }
                inside()
            }
        }
    }

    /** The name of a server of the group, chosen from the servers the group has. */
    private fun CommandNodeBuilder.withServer(inside: CommandNodeBuilder.() -> Unit) {
        argument("server", ArgumentType.string()) {
            suggests { context -> groups.get(context.get<String>("group"))?.servers.orEmpty().toList() }
            inside()
        }
    }

    /** One more word: a server when [server] is true, otherwise a plugin. */
    private fun CommandNodeBuilder.valued(server: Boolean, edit: (ServerGroups.Group, String) -> ServerGroups.Group) {
        val word = if (server) "server" else "plugin"
        argument(word, ArgumentType.string()) {
            suggests { context -> if (server) serverNames() else pluginNames(groups.get(context.get<String>("group"))) }
            executes { context ->
                val name = context.get<String>("group")
                say(context, groups.edit(name) { edit(it, context.get<String>(word)) }, "Группа $name изменена.")
            }
        }
    }

    /** A plugin after a server of the group: that server skips it, or carries it besides the rest; [extra] says which. */
    private fun CommandNodeBuilder.override(extra: Boolean) {
        argument("plugin", ArgumentType.string()) {
            suggests { context -> pluginNames(groups.get(context.get<String>("group"))) }
            executes { context ->
                val server = context.get<String>("server")
                val plugin = context.get<String>("plugin")
                val label = ServerGroups.label(server)
                val text = if (extra) "Серверу $label в группе добавлен плагин $plugin." else "Серверу $label в группе плагин $plugin не нужен."
                say(context, groups.edit(context.get<String>("group")) { it.with(server, plugin, extra) }, text)
            }
        }
    }

    private fun serverNames() = servers().map { it.name }.filterNot { it == ServerGroups.PROXY } + ServerGroups.PROXY

    private fun pluginNames(group: ServerGroups.Group?) = servers().flatMap { it.report.plugins }.map { it.product }.distinct() + group?.plugins.orEmpty()

    private fun say(context: CommandContext, saved: Boolean, done: String) {
        val kit = kit(context)
        context.sender.send(
            if (saved) kit.text(done, ChatPalette.ACCENT) else kit.text("Нет группы ${context.get<String>("group")}.", ChatPalette.DANGER),
        )
    }

    private fun of(context: CommandContext, server: String) {
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

    private fun viewServer(context: CommandContext, name: String, server: String) {
        val kit = kit(context)
        val group = groups.get(name)
        if (group == null || group.servers.none { it.equals(server, true) }) {
            context.sender.send(kit.text("В группе $name нет сервера ${ServerGroups.label(server)}.", ChatPalette.DANGER))
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

    private fun kit(context: CommandContext) = ChatKit(ChatReader.of(context.sender))

    private fun create(context: CommandContext, name: String) {
        val kit = kit(context)
        val made = groups.create(name)
        val text = when {
            made -> "Группа $name создана. Добавьте серверы и плагины: $ROOT join $name <сервер>, $ROOT require $name <плагин>"
            !ServerGroups.validName(name) -> "Имя группы: до 32 символов, без пробелов, не create и не delete."
            else -> "Группа $name уже есть или файл не записался."
        }
        context.sender.send(kit.text(text, if (made) ChatPalette.ACCENT else ChatPalette.DANGER))
    }

    private fun delete(context: CommandContext, name: String) {
        val kit = kit(context)
        val deleted = groups.delete(name)
        context.sender.send(if (deleted) kit.text("Группа $name удалена.", ChatPalette.ACCENT) else kit.text("Нет группы $name.", ChatPalette.DANGER))
    }

    private fun list(context: CommandContext) {
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

    private fun view(context: CommandContext, name: String) {
        val kit = kit(context)
        val group = groups.get(name)
        if (group == null) {
            context.sender.send(kit.text("Нет группы $name. Список: $ROOT", ChatPalette.DANGER))
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

    private fun ask(context: CommandContext, action: RemoteAction) {
        val kit = kit(context)
        val name = context.get<String>("group")
        val group = groups.get(name)
        if (group == null) {
            context.sender.send(kit.text("Нет группы $name. Список: $ROOT", ChatPalette.DANGER))
            return
        }
        val sender = context.sender
        var sent = 0
        groups.compare(group, servers()).forEach { state ->
            val products = if (action == RemoteAction.UPDATE) state.outdated else listOf(null)
            if (state.server == ServerGroups.PROXY) {
                if (products.isNotEmpty()) sender.send(kit.text("Прокси: проверка и обновление идут своими командами /pnproxy.", ChatPalette.LABEL))
                return@forEach
            }
            products.forEach { product ->
                val name = ServerGroups.label(state.server)
                val label = product?.let { "$name · $it" } ?: name
                val error = RemoteControl.send(state.server, action, product, sender.name) { result ->
                    sender.send(kit.text("$label: ${result.text}", if (result.ok) ChatPalette.ACCENT else ChatPalette.DANGER))
                }
                if (error == null) sent++ else sender.send(kit.text("$label: $error", ChatPalette.DANGER))
            }
        }
        sender.send(kit.text(if (sent == 0) "Делать нечего." else "Запросов отправлено: $sent. Ответы придут сюда.", ChatPalette.LABEL))
    }

    /** Constants of the `/pn groups` command. */
    companion object {
        /** Permission of the command, next to `pnlibrary.admin`. */
        const val PERMISSION = "pnlibrary.groups"
        private const val ROOT = "/pn groups"
    }
}
