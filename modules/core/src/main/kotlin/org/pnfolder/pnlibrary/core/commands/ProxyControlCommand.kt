package org.pnfolder.pnlibrary.core.commands

import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.CommandRegistration
import org.pnfolder.pnlibrary.api.commands.command
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.core.lifecycle.mountableCommand
import org.pnfolder.pnlibrary.core.lifecycle.registerRuntimeCommand
import org.pnfolder.pnlibrary.core.updates.catalog.ProxyUpdateCommand

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
 * | `/pn debug …` | the encrypted report for support | `pnlibrary.debug` |
 * | `/pn support` | the support link | `pnlibrary.admin` |
 *
 * It works for the console and for players, and the output is clickable wherever the sender can click.
 * `/pnlib` and `/pnlibrary` work as well, so the controls stay reachable when another plugin owns `/pn`.
 */
class ProxyControlCommand(private val library: PnLibrary) {
    /** Registers the command for this library runtime. */
    fun register(): CommandRegistration = library.registerRuntimeCommand(definition())

    /** Builds the command: the update controls and the shared `events`, `bus`, `tasks` and `debug` commands. */
    fun definition(): CommandDefinition {
        val updates = ProxyUpdateCommand(library)
        return command("pn") {
            aliases("pnlib", "pnlibrary")
            executes { updates.overview(it, OTHERS) }
            updates.install(this)
            library.mountableCommand("events")?.let { mount("events", it) }
            library.mountableCommand("debug")?.let { mount("debug", it) }
            mount("bus", EventBusCommand(library).definition())
            mount("tasks", TaskCommand(library).definition())
            mount("servers", ServersCommand().definition())
        }
    }

    private companion object {
        /** What a sender without the admin permission is told about, with the permission each command needs. */
        val OTHERS = listOf(
            "/pn events" to "pnlibrary.events.view",
            "/pn bus" to "pnlibrary.events.bus",
            "/pn tasks" to "pnlibrary.tasks",
            "/pn servers" to ServersCommand.PERMISSION,
            "/pn debug" to "pnlibrary.debug",
        )
    }
}
