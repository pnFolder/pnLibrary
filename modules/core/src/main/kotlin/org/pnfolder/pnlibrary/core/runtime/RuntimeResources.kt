package org.pnfolder.pnlibrary.core.runtime

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.commands.CommandBuilder
import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.CommandRegistration
import org.pnfolder.pnlibrary.api.commands.command
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.tasks.TaskScope

/** Private runtime capabilities, deliberately absent from the consumer API facade. */
internal interface RuntimeResources {
    val runtimeTasks: TaskScope
    fun registerRuntimeCommand(command: CommandDefinition): CommandRegistration

    /** The notice for an administrator who has just joined: recent problems from the journal, or nothing. */
    fun journalJoinNotice(): List<Component>
}

/**
 * Native pnLibrary integration only. Consumer plugins obtain tasks through ModuleContext.tasks.
 * This bridge belongs to core, not to the published public API.
 */
fun PnLibrary.runtimeTaskScope(): TaskScope = runtimeResources().runtimeTasks

/** Lines to show an administrator on joining, empty when the journal has nothing to report or the notice is off. */
fun PnLibrary.journalJoinNotice(): List<Component> = runtimeResources().journalJoinNotice()

/** Registers a pnLibrary-owned command from core or a bundled native integration. */
fun PnLibrary.registerRuntimeCommand(command: CommandDefinition): CommandRegistration =
    runtimeResources().registerRuntimeCommand(command)

/** Builds a pnLibrary-owned command; consumer plugins use ModuleContext.registerCommand. */
fun PnLibrary.registerRuntimeCommand(
    name: String,
    configure: CommandBuilder.() -> Unit,
): CommandRegistration = registerRuntimeCommand(command(name, configure))

/** Only the core runtime implements [RuntimeResources]; any other [PnLibrary] is a wiring error. */
private fun PnLibrary.runtimeResources(): RuntimeResources =
    this as? RuntimeResources ?: throw IllegalStateException(
        "Runtime resources are available only on the pnLibrary core runtime, not on ${javaClass.name}",
    )
