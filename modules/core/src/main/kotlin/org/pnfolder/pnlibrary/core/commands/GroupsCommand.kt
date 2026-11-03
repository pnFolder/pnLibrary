package org.pnfolder.pnlibrary.core.commands

import org.pnfolder.pnlibrary.api.chat.ChatNotice
import org.pnfolder.pnlibrary.api.chat.ChatTone
import org.pnfolder.pnlibrary.api.commands.ArgumentType
import org.pnfolder.pnlibrary.api.commands.CommandBuilder
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.CommandNodeBuilder
import org.pnfolder.pnlibrary.api.commands.command
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
    private val pages = GroupsPages(groups, servers)

    /** Builds the command; it is mounted under `/pn` as `groups`. */
    fun definition(): CommandDefinition = command("groups") {
        permission(PERMISSION, "pnlibrary.admin")
        executes(pages::list)
        literal("create") {
            description("Создаёт пустую группу серверов. Имя любое, до 32 символов, без пробелов.")
            argument("group", ArgumentType.string()) { executes { create(it, it.get<String>("group")) } }
        }
        withGroup("delete", "Удаляет группу. Серверы и плагины не трогает, пропадает только список.") {
            executes { delete(it, it.get<String>("group")) }
        }
        withGroup("info", "Каждый сервер группы: чего ему не хватает и что можно обновить.") { executes { pages.view(it, it.get<String>("group")) } }
        withGroup("join", "Добавляет сервер в группу. Сервер может быть в нескольких группах; прокси добавляется как @proxy.") {
            valued(true, ServerGroups.Group::addServer)
        }
        withGroup("leave", "Убирает сервер из группы вместе с его особыми настройками.") { valued(true, ServerGroups.Group::removeServer) }
        withGroup("require", "Плагин, который нужен каждому серверу группы. Если его нет, группа покажет это в списке.") {
            valued(false, ServerGroups.Group::addPlugin)
        }
        withGroup("drop", "Плагин больше не обязателен для серверов группы.") { valued(false, ServerGroups.Group::removePlugin) }
        withGroup("check", "Каждый сервер группы проверит обновления своих плагинов.") {
            permission(RemoteAction.CHECK.permission)
            executes { ask(it, RemoteAction.CHECK) }
        }
        withGroup("update", "Каждый сервер группы скачает доступные обновления плагинов группы.") {
            permission(RemoteAction.UPDATE.permission)
            executes { ask(it, RemoteAction.UPDATE) }
        }
        withGroup("server", "Показывает, что группа требует от одного сервера и чем он отличается от остальных.") {
            withServer { executes { pages.viewServer(it, it.get<String>("group"), it.get<String>("server")) } }
        }
        withGroup("skip", "Этому серверу плагин группы не нужен; у остальных серверов группы он остаётся.") { withServer { override(false) } }
        withGroup("extra", "Этому серверу нужен ещё один плагин, которого нет у остальных серверов группы.") { withServer { override(true) } }
        withGroup("reset", "Возвращает сервер к тому, что требуется от всех серверов группы.") {
            withServer {
                executes { context ->
                    val server = context.get<String>("server")
                    val done = "Сервер ${ServerGroups.label(server)} в группе теперь как все."
                    say(context, groups.edit(context.get<String>("group")) { it.reset(server) }, done)
                }
            }
        }
        literal("of") {
            description("В каких группах состоит сервер и какие плагины они от него требуют.")
            argument("server", ArgumentType.string()) {
                suggests { serverNames() }
                executes { pages.of(it, it.get<String>("server")) }
            }
        }
    }

    /** The word [word], then the name of a group. */
    private fun CommandBuilder.withGroup(word: String, about: String, inside: CommandNodeBuilder.() -> Unit) {
        literal(word) {
            description(about)
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
        if (saved) notice(context, ChatTone.SUCCESS, done)
        else notice(context, ChatTone.FAILURE, "Нет группы ${context.get<String>("group")}.", "Список: $ROOT")
    }

    /** One message in the style of the other chat commands. */
    private fun notice(context: CommandContext, tone: ChatTone, text: String, detail: String? = null) =
        groupsNotice(context, tone, text, detail)

    private fun kit(context: CommandContext) = groupsKit(context)

    private fun create(context: CommandContext, name: String) {
        when {
            groups.create(name) -> notice(context, ChatTone.SUCCESS, "Группа $name создана.", "Добавьте серверы: $ROOT join $name <сервер>")
            !ServerGroups.validName(name) -> notice(context, ChatTone.FAILURE, "Не подходит имя группы.", "До 32 символов, без пробелов.")
            else -> notice(context, ChatTone.FAILURE, "Группа $name уже есть или файл не записался.")
        }
    }

    private fun delete(context: CommandContext, name: String) {
        if (groups.delete(name)) notice(context, ChatTone.SUCCESS, "Группа $name удалена.")
        else notice(context, ChatTone.FAILURE, "Нет группы $name.", "Список: $ROOT")
    }

    private fun ask(context: CommandContext, action: RemoteAction) {
        val kit = kit(context)
        val name = context.get<String>("group")
        val group = groups.get(name)
        if (group == null) {
            notice(context, ChatTone.FAILURE, "Нет группы $name.", "Список: $ROOT")
            return
        }
        val sender = context.sender
        var sent = 0
        groups.compare(group, servers()).forEach { state ->
            val products = if (action == RemoteAction.UPDATE) state.outdated else listOf(null)
            if (state.server == ServerGroups.PROXY) {
                if (products.isNotEmpty()) notice(context, ChatTone.INFO, "Прокси: проверка и обновление идут своими командами.", "/pnproxy")
                return@forEach
            }
            products.forEach { product ->
                val name = ServerGroups.label(state.server)
                val label = product?.let { "$name · $it" } ?: name
                val error = RemoteControl.send(state.server, action, product, sender.name) { result ->
                    kit.notice(ChatNotice(if (result.ok) ChatTone.SUCCESS else ChatTone.FAILURE, label, result.text)).forEach(sender::send)
                }
                if (error == null) sent++ else notice(context, ChatTone.FAILURE, label, error)
            }
        }
        if (sent == 0) notice(context, ChatTone.INFO, "Делать нечего.")
        else notice(context, ChatTone.PROGRESS, "Запросов отправлено: $sent.", "Ответы придут сюда.")
    }

    /** Constants of the `/pn groups` command. */
    companion object {
        /** Permission of the command, next to `pnlibrary.admin`. */
        const val PERMISSION = "pnlibrary.groups"
        private const val ROOT = GROUPS_ROOT
    }
}
