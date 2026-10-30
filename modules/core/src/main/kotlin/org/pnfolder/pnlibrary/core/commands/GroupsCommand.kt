package org.pnfolder.pnlibrary.core.commands

import net.kyori.adventure.text.event.ClickEvent
import org.pnfolder.pnlibrary.api.chat.ChatKit
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.chat.ChatReader
import org.pnfolder.pnlibrary.api.commands.ArgumentType
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
 * | Command | What it does |
 * |---|---|
 * | `/pn groups` | the groups and how many of their servers are in order |
 * | `/pn groups <group>` | each server of the group: what is missing and what can be updated |
 * | `/pn groups create <group>`, `delete <group>` | makes or deletes a group |
 * | `/pn groups <group> add-server <server>`, `remove-server <server>` | changes the servers of the group |
 * | `/pn groups <group> add-plugin <plugin>`, `remove-plugin <plugin>` | changes the plugins the group asks for |
 * | `/pn groups <group> check` | every server of the group looks for updates |
 * | `/pn groups <group> update` | every server downloads the updates of the group's plugins |
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
        literal("delete") {
            argument("group", ArgumentType.string()) {
                suggests { groups.all().map { it.name } }
                executes { delete(it, it.get<String>("group")) }
            }
        }
        argument("group", ArgumentType.string()) {
            suggests { groups.all().map { it.name } }
            executes { view(it, it.get<String>("group")) }
            change("add-server") { group, value -> group.copy(servers = group.servers + value) }
            change("remove-server") { group, value -> group.copy(servers = group.servers.filterNot { it.equals(value, true) }.toSet()) }
            change("add-plugin") { group, value -> group.copy(plugins = group.plugins + value) }
            change("remove-plugin") { group, value -> group.copy(plugins = group.plugins.filterNot { it.equals(value, true) }.toSet()) }
            literal("check") {
                permission(RemoteAction.CHECK.permission)
                executes { ask(it, RemoteAction.CHECK) }
            }
            literal("update") {
                permission(RemoteAction.UPDATE.permission)
                executes { ask(it, RemoteAction.UPDATE) }
            }
        }
    }

    private fun CommandNodeBuilder.change(word: String, edit: (ServerGroups.Group, String) -> ServerGroups.Group) {
        literal(word) {
            argument("value", ArgumentType.string()) {
                suggests { context ->
                    val group = groups.get(context.get<String>("group"))
                    if (word.endsWith("server")) servers().map { it.name } else servers().flatMap { it.report.plugins }.map { it.product }.distinct() +
                        group?.plugins.orEmpty()
                }
                executes { context ->
                    val kit = kit(context)
                    val name = context.get<String>("group")
                    val saved = groups.edit(name) { edit(it, context.get<String>("value")) }
                    context.sender.send(
                        if (saved) kit.text("Группа $name изменена.", ChatPalette.ACCENT) else kit.text("Нет группы $name.", ChatPalette.DANGER),
                    )
                }
            }
        }
    }

    private fun kit(context: CommandContext) = ChatKit(ChatReader.of(context.sender))

    private fun create(context: CommandContext, name: String) {
        val kit = kit(context)
        val made = groups.create(name)
        val text = when {
            made -> "Группа $name создана. Добавьте серверы и плагины: $ROOT $name add-server …"
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
                    .clickEvent(ClickEvent.runCommand("$ROOT ${group.name}"))
            }
            lines += line
        }
        if (!kit.reader.interactive) {
            lines += kit.blank()
            lines += kit.commandHints(
                "$ROOT <группа>" to "состояние группы",
                "$ROOT create <группа>" to "новая группа",
                "$ROOT <группа> add-server <сервер>" to "добавить сервер",
                "$ROOT <группа> add-plugin <плагин>" to "плагин, который нужен",
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
        if (group.servers.isEmpty()) lines += kit.note("Серверов нет: $ROOT ${group.name} add-server <сервер>", ChatPalette.FAINT)
        groups.compare(group, servers()).forEach { state ->
            val verdict = when {
                state.known == null -> kit.text("не присылал отчёт", ChatPalette.DANGER)
                !state.known.online -> kit.text("нет связи", ChatPalette.DANGER)
                state.ok -> kit.text("всё в порядке", ChatPalette.ACCENT)
                else -> kit.text("", ChatPalette.TEXT)
            }
            var line = kit.text(" ").append(kit.text(kit.glyph('●'), if (state.ok) ChatPalette.ACCENT else ChatPalette.DANGER))
                .append(kit.text(" ${state.server}  ", ChatPalette.ACCENT)).append(verdict)
            if (state.missing.isNotEmpty()) line = line.append(kit.text("нет: ${state.missing.joinToString(", ")}  ", ChatPalette.DANGER))
            if (state.outdated.isNotEmpty()) line = line.append(kit.text("можно обновить: ${state.outdated.joinToString(", ")}", ChatPalette.SOFT))
            lines += line
        }
        lines += kit.blank()
        lines += kit.actions(
            kit.button("[Проверить]", "$ROOT ${group.name} check", "Каждый сервер группы проверит обновления", ChatPalette.ACCENT),
            kit.button("[Обновить]", "$ROOT ${group.name} update", "Скачать обновления плагинов группы на всех серверах", ChatPalette.ACCENT),
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
            products.forEach { product ->
                val label = product?.let { "${state.server} · $it" } ?: state.server
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
