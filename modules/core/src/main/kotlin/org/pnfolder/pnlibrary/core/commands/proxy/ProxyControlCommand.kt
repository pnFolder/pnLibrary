package org.pnfolder.pnlibrary.core.commands.proxy

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.chat.ChatKit
import org.pnfolder.pnlibrary.api.chat.ChatNotice
import org.pnfolder.pnlibrary.api.chat.ChatReader
import org.pnfolder.pnlibrary.api.chat.ChatTone
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandRegistration
import org.pnfolder.pnlibrary.api.commands.CommandSender
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.definition.command
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.audience.senderPointers
import org.pnfolder.pnlibrary.core.commands.configs.ConfigsCommand
import org.pnfolder.pnlibrary.core.commands.deps.DepsCommand
import org.pnfolder.pnlibrary.core.commands.events.EventBusCommand
import org.pnfolder.pnlibrary.core.commands.groups.GroupsCommand
import org.pnfolder.pnlibrary.core.commands.modules.ModulesCommand
import org.pnfolder.pnlibrary.core.commands.tasks.TaskCommand
import org.pnfolder.pnlibrary.core.lifecycle.MountableCommands
import org.pnfolder.pnlibrary.core.lifecycle.dispatchRuntimeCommand
import org.pnfolder.pnlibrary.core.lifecycle.mountableCommand
import org.pnfolder.pnlibrary.core.lifecycle.registerRuntimeCommand
import org.pnfolder.pnlibrary.core.link.Link
import org.pnfolder.pnlibrary.core.link.ServerGroups
import org.pnfolder.pnlibrary.core.link.report.LinkReports
import org.pnfolder.pnlibrary.core.link.report.NetworkServer
import org.pnfolder.pnlibrary.core.updates.catalog.ProxyUpdateCommand
import java.util.Collections
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * The one command of pnLibrary on BungeeCord and Velocity, `/pn`, the same as the Bukkit command of the same name.
 *
 * | Command | What it does | Permission |
 * |---|---|---|
 * | `/pn`, `status`, `check`, `updates …` | the list of plugins, their state, the update catalog | `pnlibrary.admin` |
 * | `/pn update <plugin>`, `update rollback`, `update confirm <token>` | stages an update, rolls back, confirms | `pnlibrary.updates.download`, `.rollback` |
 * | `/pn events …` | the journal of events | `pnlibrary.events.view` |
 * | `/pn bus …` | listeners, calls and traces of the event bus | `pnlibrary.events.bus` |
 * | `/pn tasks …` | scheduled tasks of every plugin | `pnlibrary.tasks` |
 * | `/pn servers …` | the game servers that report to this proxy | `pnlibrary.servers` |
 * | `/pn groups …` | groups of servers and the plugins they should have | `pnlibrary.groups` |
 * | `/pn debug …` | the encrypted report for support | `pnlibrary.debug` |
 * | `/pn support` | the support link | `pnlibrary.admin` |
 *
 * It works for the console and for players, and the output is clickable wherever the sender can click.
 * The proxy answers to `/pnproxy` and `/pnp`, not `/pn`, so it never hides the `/pn` of a game server; the text and
 * buttons are written with `/pn` and the platform adapters show them as `/pnproxy` (see [ProxyCommandNames]).
 */
class ProxyControlCommand(private val library: PnLibrary, private val link: Link) {
    private val built: CommandDefinition by lazy { build() }

    fun register(): CommandRegistration = library.registerRuntimeCommand(built)

    /**
     * Runs [line], a command such as `pn update rollback`, for the viewer and returns what it printed. The menu of another
     * server uses it for the buttons that run a chat command.
     */
    fun run(viewerId: UUID, viewerName: String, holds: (String) -> Boolean, line: String): List<Component> {
        val words = line.trim().split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val printed = Collections.synchronizedList(mutableListOf<Component>())
        val sender = object : CommandSender {
            override fun pointers() = senderPointers(viewerName, viewerId, holds)
            override fun send(message: Component) {
                printed += message
            }
        }
        val context = CommandContext(sender, words.drop(1), ProxyCommandNames.NAME, words.last())
        val stage = library.dispatchRuntimeCommand(built, context)
            ?: return ChatKit(ChatReader.PLAYER).notice(ChatNotice(ChatTone.FAILURE, "Команды прокси сейчас недоступны."))
        stage.toCompletableFuture().get(RUN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        return printed.toList()
    }

    fun definition(): CommandDefinition = built

    private fun build(): CommandDefinition {
        val updates = ProxyUpdateCommand(library)
        return command(ProxyCommandNames.NAME) {
            aliases(*ProxyCommandNames.ALIASES.toTypedArray())
            executes { updates.overview(it, OTHERS) }
            updates.install(this)
            for (name in MountableCommands.ALL) library.mountableCommand(name)?.let { mount(name, it) }
            mount("bus", EventBusCommand(library).definition())
            mount("tasks", TaskCommand(library).definition())
            mount("modules", ModulesCommand(library).definition())
            mount("configs", ConfigsCommand(library).definition())
            mount("deps", DepsCommand(library).definition())
            mount("servers", ServersCommand(link, ServersCommand.fromReports(link)).definition())
            mount("groups", GroupsCommand(link, withProxy()).definition())
        }
    }

    private fun withProxy(): () -> List<NetworkServer> {
        val servers = ServersCommand.fromReports(link)
        return { listOf(NetworkServer(ServerGroups.PROXY, true, LinkReports.build(library, "proxy", 0))) + servers() }
    }

    private companion object {
        const val RUN_TIMEOUT_SECONDS = 10L

        /** What a sender without the admin permission is told about, with the permission each command needs. */
        val OTHERS = listOf(
            "/pn events" to "pnlibrary.events.view",
            "/pn bus" to "pnlibrary.events.bus",
            "/pn tasks" to "pnlibrary.tasks",
            "/pn stats" to "pnlibrary.stats",
            "/pn placeholders" to "pnlibrary.placeholders",
            "/pn actions" to "pnlibrary.actions",
            "/pn settings" to "pnlibrary.settings",
            "/pn servers" to ServersCommand.PERMISSION,
            "/pn groups" to GroupsCommand.PERMISSION,
            "/pn debug" to "pnlibrary.debug",
        )
    }
}
