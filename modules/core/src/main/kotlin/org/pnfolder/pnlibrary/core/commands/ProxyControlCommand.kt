package org.pnfolder.pnlibrary.core.commands

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.CommandRegistration
import org.pnfolder.pnlibrary.api.commands.command
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.core.runtime.lifecycle.mountableCommand
import org.pnfolder.pnlibrary.core.runtime.lifecycle.registerRuntimeCommand
import org.pnfolder.pnlibrary.core.updates.catalog.ProxyUpdateCommand

/**
 * The one command of pnLibrary on BungeeCord and Velocity, `/pn`, like the Bukkit command of the same name.
 *
 * | Command | What it does |
 * |---|---|
 * | `/pn events …` | the journal of events |
 * | `/pn debug …` | the encrypted report for support |
 * | `/pn update …` | the update catalog, plan, staging and rollback; console only |
 *
 * `/pnlib` and `/pnlibrary` work as well, so the controls stay reachable when another plugin owns `/pn`.
 */
class ProxyControlCommand(private val library: PnLibrary) {
    /** Registers the command for this library runtime. */
    fun register(): CommandRegistration = library.registerRuntimeCommand(definition())

    /** Builds the command: the shared `events` and `debug` commands and the update controls mounted under it. */
    fun definition(): CommandDefinition = command("pn") {
        aliases("pnlib", "pnlibrary")
        executes { it.sender.send(Component.text("Команды pnLibrary: /pn events, /pn debug, /pn update")) }
        library.mountableCommand("events")?.let { mount("events", it) }
        library.mountableCommand("debug")?.let { mount("debug", it) }
        mount("update", ProxyUpdateCommand(library).definition())
    }
}
