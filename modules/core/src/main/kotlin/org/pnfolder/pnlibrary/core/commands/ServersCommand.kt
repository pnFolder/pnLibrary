package org.pnfolder.pnlibrary.core.commands

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.pnfolder.pnlibrary.api.chat.ChatKit
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.chat.ChatReader
import org.pnfolder.pnlibrary.api.commands.ArgumentType
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.command
import org.pnfolder.pnlibrary.api.updates.UpdateState
import org.pnfolder.pnlibrary.common.text.RussianText
import org.pnfolder.pnlibrary.core.link.LinkHub
import org.pnfolder.pnlibrary.core.link.LinkRegistry
import org.pnfolder.pnlibrary.core.updates.catalog.CatalogLabels
import java.time.Duration
import java.time.Instant

/**
 * `/pn servers` on a proxy: the game servers that report to it through the link, with their pnLibrary version,
 * players and plugins. It only shows what the servers reported; nothing is run on them.
 *
 * | Command | What it does |
 * |---|---|
 * | `/pn servers` | the list of servers; click a row to open it |
 * | `/pn servers <name>` | one server: platform, version, every plugin and its update state |
 */
class ServersCommand(private val registry: LinkRegistry = LinkHub.registry, private val linkEnabled: Boolean = true) {
    /** Builds the command; it is mounted under `/pn` as `servers`. */
    fun definition(): CommandDefinition = command("servers") {
        permission(PERMISSION, "pnlibrary.admin")
        executes(::list)
        argument("server", ArgumentType.string()) {
            suggests { registry.all().map { it.name } }
            executes { view(it, it.get<String>("server")) }
        }
    }

    private fun kit(context: CommandContext) = ChatKit(ChatReader.of(context.sender))

    private fun list(context: CommandContext) {
        val kit = kit(context)
        val servers = registry.all()
        val count = "${servers.size} ${RussianText.plural(servers.size.toLong(), "сервер", "сервера", "серверов")}"
        val lines = mutableListOf(kit.blank(), kit.header("Серверы сети", kit.text(count, ChatPalette.LABEL)))
        if (servers.isEmpty()) {
            lines += kit.note("Пока ни один сервер не прислал отчёт.", ChatPalette.FAINT)
            lines += kit.note(
                if (linkEnabled) {
                    "Скопируйте файл link.key из папки pnLibrary прокси в такую же папку игровых серверов и перезапустите их."
                } else {
                    "На прокси не удалось создать файл link.key."
                },
                ChatPalette.LABEL,
            )
        }
        servers.forEach { lines += row(kit, it) }
        lines.forEach(context.sender::send)
    }

    private fun row(kit: ChatKit, entry: LinkRegistry.Entry): Component {
        val report = entry.report
        val fresh = Duration.between(entry.receivedAt, Instant.now()).toMillis() < STALE.toMillis()
        val players = "${report.players} ${RussianText.plural(report.players.toLong(), "игрок", "игрока", "игроков")}"
        val updates = report.plugins.count { it.state == UpdateState.UPDATE_AVAILABLE.name }
        val title = kit.text(" ").append(kit.text(kit.glyph('●'), if (fresh) ChatPalette.ACCENT else ChatPalette.DANGER))
            .append(kit.text(" ${entry.name}", ChatPalette.ACCENT))
            .append(kit.text(if (fresh) "  на связи" else "  нет связи", if (fresh) ChatPalette.SOFT else ChatPalette.DANGER))
        var facts = kit.text("    ${report.platform}", ChatPalette.TEXT)
            .append(kit.dot()).append(kit.text("pnLibrary ${report.libraryVersion}", ChatPalette.SOFT))
            .append(kit.dot()).append(kit.text(players, ChatPalette.SOFT))
        if (updates > 0) facts = facts.append(kit.dot()).append(kit.text("обновлений: $updates", ChatPalette.ACCENT))
        val block = title.append(Component.newline()).append(facts)
        if (!kit.reader.interactive) return block
        return block.hoverEvent(kit.tooltip(kit.text("Нажмите, чтобы открыть сервер ›", ChatPalette.ACCENT)))
            .clickEvent(ClickEvent.runCommand("$ROOT ${entry.name}"))
    }

    private fun view(context: CommandContext, name: String) {
        val kit = kit(context)
        val entry = registry.get(name)
        if (entry == null) {
            context.sender.send(kit.text("Сервер $name не присылал отчёт. Список: $ROOT", ChatPalette.DANGER))
            return
        }
        val report = entry.report
        val lines = mutableListOf(kit.blank(), kit.header(entry.name, kit.text(TaskLabels.ago(entry.receivedAt), ChatPalette.LABEL)))
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
            lines += kit.text("  ${plugin.product} ", ChatPalette.TEXT).append(kit.text(version, ChatPalette.SOFT))
                .append(kit.dot()).append(kit.text(label, color))
        }
        lines += kit.blank()
        lines += kit.actions(kit.button("[К списку]", ROOT, "Все серверы", ChatPalette.SOFT)) ?: kit.blank()
        lines.forEach(context.sender::send)
    }

    /** Constants of the `/pn servers` command. */
    companion object {
        /** Permission of the command, next to `pnlibrary.admin`. */
        const val PERMISSION = "pnlibrary.servers"
        private const val ROOT = "/pn servers"
        private val STALE: Duration = Duration.ofMinutes(2)
    }
}
