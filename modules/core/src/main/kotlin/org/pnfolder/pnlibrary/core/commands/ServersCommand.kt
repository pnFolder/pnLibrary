package org.pnfolder.pnlibrary.core.commands

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.pnfolder.pnlibrary.api.chat.ChatKit
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.chat.ChatReader
import org.pnfolder.pnlibrary.api.commands.ArgumentType
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.CommandNodeBuilder
import org.pnfolder.pnlibrary.api.commands.command
import org.pnfolder.pnlibrary.api.updates.UpdateState
import org.pnfolder.pnlibrary.common.text.RussianText
import org.pnfolder.pnlibrary.core.link.LinkHub
import org.pnfolder.pnlibrary.core.link.LinkNames
import org.pnfolder.pnlibrary.core.link.LinkRegistry
import org.pnfolder.pnlibrary.core.link.NetworkServer
import org.pnfolder.pnlibrary.core.link.RemoteAction
import org.pnfolder.pnlibrary.core.link.RemoteControl
import org.pnfolder.pnlibrary.core.updates.catalog.CatalogLabels
import java.time.Duration
import java.time.Instant

/**
 * `/pn servers`: the servers of the network, with their pnLibrary version, players and plugins, and the update
 * controls that can be sent to them through the link. Nothing but those controls can be sent: no commands.
 *
 * | Command | What it does |
 * |---|---|
 * | `/pn servers` | the list of servers; click a row to open it |
 * | `/pn servers <name>` | one server: platform, version, every plugin and its update state, and buttons |
 * | `/pn servers <name> check` | asks the server to look for updates now |
 * | `/pn servers <name> update <plugin>` | asks the server to download the update of a plugin |
 * | `/pn servers <name> auto <plugin> on\|off` | turns automatic download of a plugin on or off there |
 *
 * On a proxy the list comes from the reports of the game servers; on a game server it comes from the map of the
 * network that the proxy sent, with the proxy itself as `proxy`.
 *
 * @param servers the servers to show now
 */
class ServersCommand(private val servers: () -> List<NetworkServer>) {
    /** Builds the command; it is mounted under `/pn` as `servers`. */
    fun definition(): CommandDefinition = command("servers") {
        permission(PERMISSION, "pnlibrary.admin")
        executes(::list)
        argument("server", ArgumentType.string()) {
            suggests { servers().map { it.name } }
            executes { view(it, it.get<String>("server")) }
            action("check", RemoteAction.CHECK)
            literal("update") { plugin(RemoteAction.UPDATE) }
            literal("auto") {
                permission(RemoteAction.AUTO_ON.permission)
                argument("plugin", ArgumentType.string()) {
                    suggests { plugins(it.get<String>("server")) }
                    literal("on") { executes { send(it, RemoteAction.AUTO_ON, it.get<String>("plugin")) } }
                    literal("off") { executes { send(it, RemoteAction.AUTO_OFF, it.get<String>("plugin")) } }
                }
            }
        }
    }

    private fun CommandNodeBuilder.action(word: String, action: RemoteAction) {
        literal(word) {
            permission(action.permission)
            executes { send(it, action, null) }
        }
    }

    private fun CommandNodeBuilder.plugin(action: RemoteAction) {
        permission(action.permission)
        argument("plugin", ArgumentType.string()) {
            suggests { plugins(it.get<String>("server")) }
            executes { send(it, action, it.get<String>("plugin")) }
        }
    }

    private fun plugins(server: String): List<String> =
        servers().firstOrNull { it.name.equals(server, ignoreCase = true) }?.report?.plugins.orEmpty().map { it.product }

    private fun kit(context: CommandContext) = ChatKit(ChatReader.of(context.sender))

    private fun send(context: CommandContext, action: RemoteAction, product: String?) {
        val kit = kit(context)
        val name = context.get<String>("server")
        val target = servers().firstOrNull { it.name.equals(name, ignoreCase = true) }?.name ?: name
        val sender = context.sender
        val error = RemoteControl.send(target, action, product, sender.name) { result ->
            sender.send(kit.text("$target: ${result.text}", if (result.ok) ChatPalette.ACCENT else ChatPalette.DANGER))
        }
        sender.send(kit.text(error ?: "Запрос отправлен на $target…", if (error == null) ChatPalette.LABEL else ChatPalette.DANGER))
    }

    private fun list(context: CommandContext) {
        val kit = kit(context)
        val all = servers()
        val count = "${all.size} ${RussianText.plural(all.size.toLong(), "сервер", "сервера", "серверов")}"
        val lines = mutableListOf(kit.blank(), kit.header("Серверы сети", kit.text(count, ChatPalette.LABEL)))
        if (all.isEmpty()) {
            lines += kit.note("Пока ни один сервер не прислал отчёт.", ChatPalette.FAINT)
            lines += kit.note(
                if (LinkHub.key != null) {
                    "Скопируйте файл link.key из папки pnLibrary прокси в такую же папку игровых серверов и перезапустите их."
                } else {
                    "Связь выключена: нет файла link.key."
                },
                ChatPalette.LABEL,
            )
        }
        all.forEach { lines += row(kit, it) }
        lines.forEach(context.sender::send)
    }

    private fun row(kit: ChatKit, server: NetworkServer): Component {
        val report = server.report
        val players = "${report.players} ${RussianText.plural(report.players.toLong(), "игрок", "игрока", "игроков")}"
        val updates = report.plugins.count { it.state == UpdateState.UPDATE_AVAILABLE.name }
        val title = kit.text(" ").append(kit.text(kit.glyph('●'), if (server.online) ChatPalette.ACCENT else ChatPalette.DANGER))
            .append(kit.text(" ${server.name}", ChatPalette.ACCENT))
            .append(kit.text(if (server.online) "  на связи" else "  нет связи", if (server.online) ChatPalette.SOFT else ChatPalette.DANGER))
        var facts = kit.text("    ${report.platform}", ChatPalette.TEXT)
            .append(kit.dot()).append(kit.text("pnLibrary ${report.libraryVersion}", ChatPalette.SOFT))
            .append(kit.dot()).append(kit.text(players, ChatPalette.SOFT))
        if (updates > 0) facts = facts.append(kit.dot()).append(kit.text("обновлений: $updates", ChatPalette.ACCENT))
        val block = title.append(Component.newline()).append(facts)
        if (!kit.reader.interactive) return block
        return block.hoverEvent(kit.tooltip(kit.text("Нажмите, чтобы открыть сервер ›", ChatPalette.ACCENT)))
            .clickEvent(ClickEvent.runCommand("$ROOT ${server.name}"))
    }

    private fun view(context: CommandContext, name: String) {
        val kit = kit(context)
        val server = servers().firstOrNull { it.name.equals(name, ignoreCase = true) }
        if (server == null) {
            context.sender.send(kit.text("Сервер $name не присылал отчёт. Список: $ROOT", ChatPalette.DANGER))
            return
        }
        val report = server.report
        val lines = mutableListOf(kit.blank(), kit.header(server.name, kit.text(TaskLabels.ago(report.sentAt), ChatPalette.LABEL)))
        lines += kit.field("Сервер", kit.text(report.platform, ChatPalette.TEXT))
        lines += kit.field("pnLibrary", kit.text(report.libraryVersion, ChatPalette.TEXT))
        lines += kit.field("Игроков", kit.text(report.players.toString(), ChatPalette.TEXT))
        lines += kit.blank()
        lines += kit.section("Плагины с обновлениями")
        if (report.plugins.isEmpty()) lines += kit.note("Ни один плагин не подключён к обновлениям pnLibrary.", ChatPalette.FAINT)
        report.plugins.forEach { plugin ->
            val state = runCatching { UpdateState.valueOf(plugin.state) }.getOrNull()
            val label = state?.let(CatalogLabels::stateLabel) ?: plugin.state
            val color = state?.let(CatalogLabels::stateColor) ?: ChatPalette.SOFT
            val version = plugin.latest?.let { "v${plugin.version} » v$it" } ?: "v${plugin.version}"
            var line = kit.text("  ${plugin.product} ", ChatPalette.TEXT).append(kit.text(version, ChatPalette.SOFT))
                .append(kit.dot()).append(kit.text(label, color))
            if (plugin.latest != null && kit.reader.interactive && context.sender.hasPermission(RemoteAction.UPDATE.permission)) {
                line = line.append(kit.text(" ")).append(
                    kit.button("[Обновить]", "$ROOT ${server.name} update ${plugin.product}", "Скачать обновление на ${server.name}", ChatPalette.ACCENT),
                )
            }
            lines += line
        }
        lines += kit.blank()
        val check = kit.button("[Проверить]", "$ROOT ${server.name} check", "Попросить сервер проверить обновления", ChatPalette.ACCENT)
        lines += kit.actions(check, kit.button("[К списку]", ROOT, "Все серверы", ChatPalette.SOFT)) ?: kit.blank()
        lines.forEach(context.sender::send)
    }

    /** Constants and the sources of the `/pn servers` command. */
    companion object {
        /** Permission of the command, next to `pnlibrary.admin`. */
        const val PERMISSION = "pnlibrary.servers"
        private const val ROOT = "/pn servers"
        private val STALE: Duration = Duration.ofMinutes(2)

        /** The servers a proxy heard from, as they are listed there. */
        fun fromReports(registry: LinkRegistry = LinkHub.registry): () -> List<NetworkServer> = {
            registry.all().map { NetworkServer(it.name, Duration.between(it.receivedAt, Instant.now()) < STALE, it.report) }
        }

        /** The proxy and the servers of the network map a game server received, with the proxy first. */
        fun fromNetworkMap(): () -> List<NetworkServer> = {
            LinkHub.network?.report?.let { listOf(NetworkServer(LinkNames.PROXY, true, it.proxy)) + it.servers }.orEmpty()
        }
    }
}
